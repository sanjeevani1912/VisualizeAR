// Test helper for api-test.ts: edits audit entry 3 in a THROWAWAY Node database (path given as the argument),
// so the test can check that the chain verification notices.   node tamper_audit_node.mjs <path to dhruvar.db>
import { DatabaseSync } from 'node:sqlite'

const file = process.argv[2]
if (!file || /server[\\/]data/.test(file)) throw new Error('give the path of a throwaway database')
const db = new DatabaseSync(file)
db.exec("UPDATE audit SET detail = 'edited' WHERE seq = 3")
db.close()
