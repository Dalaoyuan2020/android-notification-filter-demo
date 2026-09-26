"""Disposable localhost-only HTTPS Jev fixture; never calls a model or external service.

Requires host Python cryptography. Private keys stay in the caller-specified work directory.
The Android test receives only ca_der_base64, never the CA/server private key.
"""
import argparse
import base64
import datetime as dt
import ipaddress
import json
import os
from pathlib import Path
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

TEAM_MODELS = {"local-systemone-ft", "local-systemone-v1", "typesafe-jev", "bocha-jev"}


def certificates(directory):
    now = dt.datetime.now(dt.timezone.utc)
    ca_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    ca_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Disposable Jev Test CA")])
    ca = (x509.CertificateBuilder().subject_name(ca_name).issuer_name(ca_name)
          .public_key(ca_key.public_key()).serial_number(x509.random_serial_number())
          .not_valid_before(now - dt.timedelta(minutes=5)).not_valid_after(now + dt.timedelta(days=1))
          .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
          .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False,
                         key_encipherment=False, data_encipherment=False, key_agreement=False,
                         key_cert_sign=True, crl_sign=True, encipher_only=False, decipher_only=False), critical=True)
          .sign(ca_key, hashes.SHA256()))
    server_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    server = (x509.CertificateBuilder()
              .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")]))
              .issuer_name(ca.subject).public_key(server_key.public_key())
              .serial_number(x509.random_serial_number())
              .not_valid_before(now - dt.timedelta(minutes=5)).not_valid_after(now + dt.timedelta(days=1))
              .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
              .add_extension(x509.SubjectAlternativeName([x509.DNSName("localhost"),
                              x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]), critical=False)
              .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
              .sign(ca_key, hashes.SHA256()))
    for name, key in (("ca-key.pem", ca_key), ("server-key.pem", server_key)):
        path = directory / name
        path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                         serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        try:
            os.chmod(path, 0o600)
        except OSError:
            pass
    (directory / "ca.pem").write_bytes(ca.public_bytes(serialization.Encoding.PEM))
    (directory / "server.pem").write_bytes(server.public_bytes(serialization.Encoding.PEM))
    return base64.b64encode(ca.public_bytes(serialization.Encoding.DER)).decode("ascii")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--port", type=int, default=0)
    args = parser.parse_args()
    directory = args.state_dir.resolve()
    directory.mkdir(parents=True, exist_ok=True)
    ca_base64 = certificates(directory)
    lock = threading.Lock()
    requests_path = directory / "requests.jsonl"
    requests_path.write_text("", encoding="utf-8")

    class Handler(BaseHTTPRequestHandler):
        server_version = "LocalJevFixture/1"

        def log_message(self, _format, *_args):
            pass  # Do not echo request headers, tokens, or content into process logs.

        def send_json(self, status, payload):
            encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def do_GET(self):
            self.send_json(200 if self.path == "/health" else 404, {"fixture": True})

        def do_POST(self):
            status, body, error = 200, None, None
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > 65536:
                    raise ValueError("invalid request length")
                body = json.loads(self.rfile.read(length).decode("utf-8"))
                if self.path not in ("/official/v1/systemone", "/relay/v1/systemone", "/bocha/v1/systemone", "/jev/v1/systemone"):
                    raise ValueError("unexpected route")
                state = body["state"]
                question = body["questions"]["keep"]
                if not isinstance(body["model"], str) or not body["model"]:
                    raise ValueError("missing model")
                if self.path == "/jev/v1/systemone" and body["model"] not in TEAM_MODELS:
                    raise ValueError("unexpected 1052 model")
                if not all(isinstance(state[key], str) for key in ("来源", "标题", "消息")):
                    raise ValueError("invalid state")
                if "近期行为" in state and not isinstance(state["近期行为"], str):
                    raise ValueError("invalid recent behavior")
                if question["type"] != "choice" or set(question["criteria"]) != {"重要", "广告"}:
                    raise ValueError("invalid choice question")
            except (ValueError, KeyError, TypeError, UnicodeError) as exc:
                status, error = 422, str(exc)
            entry = {"path": self.path, "status": status, "body": body,
                     "authorization_present": bool(self.headers.get("Authorization")), "error": error}
            with lock:
                with requests_path.open("a", encoding="utf-8") as stream:
                    stream.write(json.dumps(entry, ensure_ascii=False) + "\n")
            if status != 200:
                self.send_json(status, {"error": error})
            else:
                self.send_json(200, {"answers": {"keep": {"type": "choice", "choice": "重要",
                                                          "probabilities": {"重要": 0.7, "广告": 0.3}}}})

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(directory / "server.pem", directory / "server-key.pem")
    server.socket = context.wrap_socket(server.socket, server_side=True)
    ready = {"port": server.server_port, "ca_der_base64": ca_base64, "pid": os.getpid()}
    temporary = directory / "ready.tmp"
    temporary.write_text(json.dumps(ready), encoding="utf-8")
    temporary.replace(directory / "ready.json")
    print(f"Local HTTPS fixture ready on 127.0.0.1:{server.server_port}", flush=True)
    try:
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
