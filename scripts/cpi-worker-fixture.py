"""DEMO-only HTTPS fixture, mounted only into the isolated local rehearsal.

Never shipped in the API image, never contact BLS, never print request bodies.
The private Docker DNS alias and ephemeral test truststore exercise the real
client's fixed origin without adding a production endpoint override.
"""
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import ssl


def response_bytes(now):
    month = now.replace(day=1) - timedelta(days=1)
    rows = [
        {"year": str(month.year), "period": f"M{month.month:02d}", "value": "103.050",
         "footnotes": [{"code": "X", "text": "DEMO offline CPI worker test; not BLS data"}]},
        {"year": str(month.year - 1), "period": f"M{month.month:02d}", "value": "100.000", "footnotes": []},
    ]
    return json.dumps({"status": "REQUEST_SUCCEEDED", "responseTime": 1, "message": [],
                       "Results": {"series": [{"seriesID": series, "data": rows} for series in
                                              ("CUUR0000SA0", "CUUR0000SA0L1E")]}},
                      separators=(",", ":")).encode()


def valid_request(value, year):
    return value == {"seriesid": ["CUUR0000SA0", "CUUR0000SA0L1E"], "startyear": str(year - 3),
                     "endyear": str(year), "registrationkey": "SyntheticCpiContainerKeyOnly",
                     "catalog": False, "calculations": False, "annualaverage": False, "aspects": False} \
        and all(value[key] is False for key in ("catalog", "calculations", "annualaverage", "aspects"))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        print("DEMO_CPI_FIXTURE_REQUEST received", flush=True)
        self.connection.settimeout(5)
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if self.path != "/publicAPI/v2/timeseries/data/" or not 0 < size <= 1024:
                raise ValueError()
            now = datetime.now(timezone.utc)
            if not valid_request(json.loads(self.rfile.read(size)), now.year):
                raise ValueError()
            mode = Path("/fixture/mode").read_text().strip()
            if mode not in {"success", "rate-limit", "malformed"}:
                raise ValueError()
            raw = response_bytes(now) if mode == "success" else b'{"DEMO":"invalid source response"}'
            self.send_response(429 if mode == "rate-limit" else 200)
            if mode == "rate-limit":
                self.send_header("Retry-After", "172800")
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
        except (ValueError, OSError):
            self.send_error(400, "DEMO fixture request rejected")


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 443), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain("/fixture/cert.pem", "/fixture/key.pem")
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print("DEMO_CPI_FIXTURE_READY", flush=True)
    server.serve_forever()
