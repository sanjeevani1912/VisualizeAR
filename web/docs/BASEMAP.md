# Dashboard basemap (map server)

For a beginner. The dashboard map can show the same basemap as the phone (roads, land, labels of India) behind the plan,
so an officer can pan around nearby areas. Without it the map is a plain beige background with the region, grid and assets.

## Turn it on

Set `VITE_MAP_STYLE_URL` to the address of the map server's `style.json` **when you build or start the dashboard**
(Vite bakes the value in, so changing it later needs a rebuild):

| Situation | Value |
|---|---|
| Dashboard and map behind Nginx on one address (the normal setup, see `docs/MAP-AND-NGINX.md` in the infra branch) | `/map/style.json` |
| Another PC | `http://<that PC>:8000/map/style.json` |
| Not set | offline beige map, nothing is requested |

```powershell
# development (dashboard on 5173, map server already running on 8000)
$env:VITE_MAP_STYLE_URL = '/map/style.json'
npm run dev

# production build for Nginx
$env:VITE_MAP_STYLE_URL = '/map/style.json'
npm run build        # then Nginx serves web/dist
```
**Windows warning:** use PowerShell for these commands. In Git Bash, `VITE_MAP_STYLE_URL=/map/style.json npm run build` is silently
rewritten to `C:/Program Files/Git/map/style.json` (Git Bash turns anything that starts with `/` into a Windows path), the
map then cannot find the style and the dashboard shows the offline fallback. Quick check after a build: the string `/map/style.json`
should appear in `dist/assets/*.js`, and `Program Files` should not.

A relative value such as `/map/style.json` means "same address as this page", so nothing in the code mentions a PC address.
In development, `npm run dev` forwards `/map/...` to Nginx (`http://localhost:8000`, change with `DEV_MAP_PROXY`).
It also passes the browser's own address through, because Nginx writes that address into the style (see the infra notes).

## If the map server is down

Before drawing, the dashboard fetches `style.json` (8 second limit). If that fails, or the file is not a map style, the map falls
back to the offline background and a small notice says **"Basemap unavailable: showing the plan only"**. The plan, grid, icons and
all buttons keep working. The same notice appears if tiles start failing later in the session (server errors or no connection;
a single missing tile, 404, is normal at the edge of the data and is ignored). The notice goes away on the next page load once the
server is back.

## Printing

A live map cannot redraw itself to the printed page, so just before printing the page swaps the live map for a picture of exactly
what is on screen (the plan on the basemap) and prints the map credit under it. Pan and zoom to the area you want, then use
**Print plan**. After printing the live map comes back.

## Credit (attribution)

With the basemap, the tile description served by the map server already carries "© OpenStreetMap contributors" and
"Boundaries: Survey of India via DataMeet (CC BY 4.0)", and the map shows it bottom right (and in print). Do not remove it from the
server. Without the basemap the corner says "Offline view · region, grid and assets only".

## Offline rule

No CDN, no external fonts, no external tile server: only the address you configure is contacted, and it should be your own map server.

## How it was checked (2026-11-20)

- Map server and Nginx running on this PC. Dashboard dev server with `VITE_MAP_STYLE_URL=/map/style.json`: the style, tiles and fonts
  came through the dashboard's own address; the app's export plan (Delhi) and a sample plan (Goa) were drawn on the real basemap
  (`screenshots-c4/basemap-delhi-plan.jpg`, `basemap-goa-plan.jpg`).
- **Production build behind Nginx:** a second, temporary Nginx container (port 8010, same config as the real one, serving this
  branch's `web/dist`) was used so the running Nginx was not touched. Opened by this PC's **network address**
  (`http://192.168.1.42:8010`): sign-in, plan list, and the basemap loaded, and every map request (style, tile description,
  fonts, tiles) went to that same address (`nginx-network-address.jpg`). The style came back with the address used in each URL.
- **Map server lost while a page is open:** the temporary Nginx's map route was pointed at a dead port (the real map server was not
  stopped); after zooming out, the notice appeared and the plan stayed intact (`midsession-failure-notice.jpg`). The route was restored.
- Fallback with a wrong map address: notice shown, plan usable (`fallback-notice.jpg`; this also happened by accident once, when the
  Git Bash path mix-up above gave a wrong address).
- Print: the page's print rules were applied on screen at A4 width and the `beforeprint` step was triggered by hand
  (`print-with-basemap.jpg`). This found that the live map would have been cut off when printed, which the picture swap fixes.
- `npm run typecheck` and `npm run build` pass.

## Not checked

- **A real phone or another PC** reaching the dashboard (only this PC, via its network address). Windows Firewall for port 8000/8010 was
  not tested (connections from the PC itself do not pass through it); a phone on Wi-Fi may need a firewall rule.
- **HTTPS.** Nginx writes `$scheme` into the style addresses. Behind a proxy that ends HTTPS and talks plain HTTP to this Nginx, those
  addresses would start with `http://` and browsers would block them (mixed content). Before using HTTPS, change the Nginx rewrite to
  use `X-Forwarded-Proto` (this is in the infra notes too). Not tried.
- The real Nginx on port 8000 serving a build with the basemap (the temporary copy used the same configuration and files).
- A real print dialog or PDF (only the simulation above), and print with a very large map picture on slow printers.
- Basemap tile loading is slow in the embedded browser (up to about 30 seconds for the first paint); on a normal browser it was not measured.
