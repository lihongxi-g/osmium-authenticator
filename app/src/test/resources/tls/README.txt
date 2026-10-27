Unit-test-only TLS material for the WebDAV client tests.

Both key pairs were generated for these tests and are throwaway: they are not
used by the app, they are not trusted by anything, and they exist only so the
test can run a real TLS server on 127.0.0.1 and assert that the client

  * completes a handshake against a certificate it is told to trust
    (test-cert.pem, SAN: DNS:localhost, IP:127.0.0.1), and
  * rejects a certificate whose name does not match the server it connected to
    (wrong-name-cert.pem, SAN: DNS:not-this-server.example).

Committed on purpose (rather than generated at test time) so the private keys
are stable and nothing has to shell out to openssl during a build.
