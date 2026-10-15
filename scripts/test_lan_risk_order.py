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


if __name__ == "__main__":
    unittest.main()