Context (last updated: now)
- Admin Web: Articles are cards; Users table remains DataGrid.
- Dark theme uses creative gradient background; overflowX hidden to avoid horizontal page scroll.
- Fixed Users date columns: Registered uses valueGetter from row.createdAt; Last login has robust getter/formatter.
- Deployed successfully to /var/www/LWB/Admin with bundle assets/index-Bqc5HwIp.js.

Next:
- Verify in production that Users “Registered” and “Last login” dates render for multiple rows. If any row shows blank, capture sample payload and adjust getters/formatters.
- Optional: consider card polish (order badge overlay; small content snippet) as a follow-up.