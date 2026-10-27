import fs from 'node:fs'
import path from 'node:path'
import cookieParser from 'cookie-parser'
import express, { type NextFunction, type Request, type Response } from 'express'
import helmet from 'helmet'
import multer from 'multer'
import { verifyAuditChain } from './audit.js'
import { csrfGuard, HttpError, loadUser, purgeExpiredSessions, requireRole } from './auth.js'
import { config } from './config.js'
import { adminRouter, authRouter, userRouter } from './users.js'
import { planRouter } from './plans.js'

const app = express()
if (config.trustProxy) app.set('trust proxy', 1)
app.disable('x-powered-by')

const mapOrigin = config.mapOrigin ? [config.mapOrigin] : []
app.use(
  helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        scriptSrc: ["'self'"],
        styleSrc: ["'self'", "'unsafe-inline'"], // MapLibre sets inline styles
        imgSrc: ["'self'", 'data:', 'blob:', ...mapOrigin],
        connectSrc: ["'self'", ...mapOrigin],
        workerSrc: ["'self'", 'blob:'],
        frameAncestors: ["'none'"],
        objectSrc: ["'none'"],
      },
    },
  }),
)
app.use(express.json({ limit: '2mb' }))
app.use(cookieParser())

const api = express.Router()
api.use((_req, res, next) => {
  res.setHeader('Cache-Control', 'no-store')
  next()
})
api.use(loadUser)
api.use(csrfGuard)
api.get('/health', (_req, res) => res.json({ ok: true }))
api.use(authRouter)
api.use(userRouter)
api.use('/admin', adminRouter)
api.get('/audit/verify', requireRole('admin', 'auditor'), (_req, res) => res.json(verifyAuditChain()))
api.use(planRouter)
api.use((_req, _res, next) => next(new HttpError(404, 'Not found')))
app.use('/api', api)

// In production the built dashboard is served from the same origin, so no CORS is needed.
if (fs.existsSync(path.join(config.webDist, 'index.html'))) {
  app.use(express.static(config.webDist, { index: false, maxAge: '1h' }))
  app.get(/^\/(?!api\/).*/, (_req, res) => res.sendFile(path.join(config.webDist, 'index.html')))
}

app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
  if (err instanceof HttpError) {
    res.status(err.status).json({ error: err.message })
  } else if (err instanceof multer.MulterError) {
    res.status(err.code === 'LIMIT_FILE_SIZE' ? 413 : 400).json({ error: err.code === 'LIMIT_FILE_SIZE' ? 'Image is too large' : 'Invalid upload' })
  } else if (err instanceof SyntaxError && 'status' in err) {
    res.status(400).json({ error: 'Malformed JSON' })
  } else {
    console.error(err)
    res.status(500).json({ error: 'Something went wrong on the server' })
  }
})

setInterval(purgeExpiredSessions, 10 * 60 * 1000).unref()
purgeExpiredSessions()

app.listen(config.port, () => {
  console.log(`DhruvAR API listening on http://localhost:${config.port}  (data: ${config.dataDir})`)
})
