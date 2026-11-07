"""Guard the production call order: approval must precede vault transmission."""
from pathlib import Path
import unittest

SRC = Path(__file__).resolve().parents[1] / "app/src/main/java/com/safekey/authenticator"


class LanRiskOrderTest(unittest.TestCase):
    def test_sender_checks_local_risk_before_export(self):
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        send = screen[screen.index("private fun SendTabContent"):screen.index("private fun ReceiveTabContent")]
        self.assertLess(send.index("confirmRisk(selfReport, null)"), send.index("exportVault("))

    def test_receiver_checks_local_risk_before_listening(self):
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        receive = screen[screen.index("private fun ReceiveTabContent"):]
        self.assertLess(receive.index("confirmRisk(report, null)"), receive.index("server.start("))

    def test_warning_is_not_triggered_by_delivery(self):
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        self.assertNotIn("showRiskDialog = true", screen)

    def test_payload_uses_mutual_consent(self):
        client = (SRC / "network/LanTransferClient.kt").read_text()
        server = (SRC / "network/LanTransferServer.kt").read_text()
        self.assertIn("LanTransferConsent.send", client)
        self.assertIn("LanTransferConsent.authorize", server)
        self.assertLess(server.index("LanTransferConsent.authorize"), server.index("LanWire.readPayload"))

    def test_receiver_separates_a_wrong_code_from_a_user_cancel(self):
        server = (SRC / "network/LanTransferServer.kt").read_text()
        self.assertIn("const val ERR_PROOF", server)
        self.assertIn("else ERR_PROOF", server)
        # Only a proved-before-prompted failure may spend the attempt budget.
        self.assertIn("charge = !prompted", server)
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        self.assertIn("LanTransferServer.ERR_PROOF", screen)

    def test_a_stranger_cannot_spend_the_attempt_budget(self):
        server = (SRC / "network/LanTransferServer.kt").read_text()
        # Junk/timeouts before the code was proven are the limiter's business.
        self.assertIn("charge = proofVerified", server)
        self.assertNotIn("failed = true", server)
        self.assertIn("limiter.admit", server)
        self.assertIn("limiter.release()", server)

    def test_a_junk_greeting_is_not_reported_as_a_version_mismatch(self):
        server = (SRC / "network/LanTransferServer.kt").read_text()
        self.assertIn("Greeting.WrongVersion", server)
        self.assertIn("Greeting.Malformed", server)
        # The version notice is rate limited, not sprayed per connection.
        self.assertIn("reportProtocolMismatch", server)

    def test_unknown_scan_state_requires_confirmation(self):
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        self.assertIn("report != null && !report.hasThreats", screen)
        # A report whose device table could not be read is unknown as well, and
        # "read but empty" is a separate case with its own wording.
        self.assertIn("report.arpChecksRan", screen)
        self.assertIn("localReport.arpReadable", screen)
        collector = (SRC / "network/LanThreatCollector.kt").read_text()
        self.assertIn("arpChecksRan", collector)
        self.assertIn("arpReadable", collector)

    def test_sender_refuses_a_non_local_target(self):
        client = (SRC / "network/LanTransferClient.kt").read_text()
        self.assertIn("LanSendTarget.isLocalPeer", client)
        self.assertLess(client.index("LanSendTarget.isLocalPeer"), client.index("socket.connect"))
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        self.assertIn("LanSendTarget.literalIsLocalUnicast", screen)

    def test_the_pairing_code_expires(self):
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        receive = screen[screen.index("private fun ReceiveTabContent"):]
        self.assertIn("PAIRING_CODE_IDLE_TIMEOUT_MS", receive)
        self.assertIn("Lifecycle.Event.ON_PAUSE", receive)
        self.assertIn("stopListening(lan.idleStopped)", receive)


if __name__ == "__main__":
    unittest.main()
