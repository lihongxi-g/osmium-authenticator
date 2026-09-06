// Format fixture provenance
//
// Files aegis_plain.json, aegis_encrypted.json, andotp_plain.json,
// 2fas_v1.json, 2fas_v2.json, 2fas_v3.json, 2fas_v4.json and
// 2fas_v4_encrypted.json are VERBATIM copies of real export files taken from
// the Aegis Authenticator test suite:
//   https://github.com/beemdevelopment/Aegis
//   app/src/test/resources/com/beemdevelopment/aegis/importers/
// Aegis is GPL-3.0; Osmium is GPL-3.0-or-later — reuse is compatible. The
// entries are synthetic test data (secrets are non-functional placeholders).
//
// raivo_sample.json / raivo_single_object.json follow the real Raivo OTP
// legacy export sample published in the OtpTranslate article
// (https://tygertec.com/aegis-raivo-otp-translator) and accepted by Ente
// Auth's Raivo importer; values are constructed equivalents.
//
// lastpass_accounts.json is constructed from the field mapping documented by
// the community converters tghw/lastpass_aegis_convert and
// PaulSorensen/lastpass-2fa-converter (both MIT).
