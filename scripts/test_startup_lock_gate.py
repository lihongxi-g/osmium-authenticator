#!/usr/bin/env python3
"""Check that the startup gate is wired into the Activity and lifecycle."""

from pathlib import Path
import unittest


SOURCE = Path(__file__).resolve().parents[1] / (
    "app/src/main/java/com/safekey/authenticator"
)
ACTIVITY = (SOURCE / "MainActivity.kt").read_text(encoding="utf-8")
MODEL = (SOURCE / "MainViewModel.kt").read_text(encoding="utf-8")


class StartupLockGateTest(unittest.TestCase):
    def test_first_frame_uses_one_gate_snapshot_and_can_show_only_the_background(self):
        self.assertIn("val gate by vm.appGate.collectAsState()", ACTIVITY)
        self.assertRegex(
            ACTIVITY,
            r"destroyed -> DestroyedScreen\(\)\s+!gate\.ready -> Unit\s+"
            r"gate\.pinRequired -> PinGate\(\)\s+gate\.locked -> LockGate\(\)\s+"
            r"canShowMain -> MainNavHost\(",
        )

    def test_initial_decision_waits_for_loaded_settings_and_foreground(self):
        self.assertIn("MutableStateFlow(AppGateState())", MODEL)
        collector = MODEL.split("settings.collect { s ->", 1)[1].split(
            "// Detection logs", 1
        )[0]
        self.assertRegex(collector, r"if \([^\n]*!s\.loaded[^\n]*\) return@collect")
        self.assertIn("if (!appInForeground && !_appGate.value.ready) return@collect", collector)
        self.assertIn("applyGate(s)", collector)
        self.assertRegex(
            MODEL,
            r"private fun applyGate\([^\n]*\) \{\s+if \(!s\.loaded\) return\s+"
            r"_appGate\.value = _appGate\.value\.applyDecision\(",
        )

    def test_notification_and_overlays_wait_for_unlocked_content(self):
        self.assertIn(
            "val canShowMain = settings.loaded && gate.isOpen && !destroyed && !tamperBlocked",
            ACTIVITY,
        )
        self.assertIn("notificationEntry, canShowMain,", ACTIVITY)
        self.assertIn("if (!notificationEntry || !canShowMain) return@LaunchedEffect", ACTIVITY)
        for guard in (
            "if (entryAuthPending && canShowMain)",
            "val showWelcome = canShowMain &&",
            "if (update != null && canShowMain && !showWelcome)",
            "!settings.pinReminderSilenced && canShowMain",
            "if (notice != null && canShowMain)",
        ):
            with self.subTest(guard=guard):
                self.assertIn(guard, ACTIVITY)

    def test_known_integrity_restriction_is_applied_before_loaded_settings_are_published(self):
        effective_settings = MODEL.split("val settings: StateFlow<AppSettings> =", 1)[1].split(
            "/** Saved WebDAV", 1
        )[0]
        self.assertRegex(
            effective_settings,
            r"combine\(settingsRepo\.settings, RootState\.report\) \{ s, report ->",
        )
        self.assertRegex(
            effective_settings,
            r"if \(isRootRestricted\(s, report\)\) \{[^{}]*s\.copy\(\s*gateOnOpen = true",
        )
        self.assertRegex(ACTIVITY, r"vm\.setBiometricState\(biometricState\(\)\)\s+vm\.onAppForeground\(\)")


if __name__ == "__main__":
    unittest.main()