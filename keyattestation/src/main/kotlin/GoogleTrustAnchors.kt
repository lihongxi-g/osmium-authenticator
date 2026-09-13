/*
 * Copyright 2026 SafeKey project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// NOTE: This file is NOT from upstream. The vendored copy of
// https://github.com/android/keyattestation normally obtains this class from
// a build-time source-generation task that inlines roots.json as a Kotlin
// string constant. This project replaces that with a small runtime loader
// that reads the same roots.json from the classpath. See ../NOTICE.

package com.android.keyattestation.verifier

import com.google.gson.Gson
import java.security.cert.TrustAnchor

/**
 * The Google root certificates used for Android Key Attestation, loaded from
 * the `roots.json` resource bundled with this library.
 *
 * The data mirrors https://android.googleapis.com/attestation/root; see the
 * upstream repository for the refresh process.
 */
object GoogleTrustAnchors : () -> Set<TrustAnchor> {
  private val anchors: Set<TrustAnchor> by lazy { load() }

  override fun invoke(): Set<TrustAnchor> = anchors

  private fun load(): Set<TrustAnchor> {
    val loader = GoogleTrustAnchors::class.java.classLoader
    val stream =
      checkNotNull(loader?.getResourceAsStream(RESOURCE_NAME)) {
        "Unable to load $RESOURCE_NAME from the classpath"
      }
    val json = stream.use { it.readBytes().decodeToString() }
    return Gson()
      .fromJson(json, Array<String>::class.java)
      .map { TrustAnchor(it.asX509Certificate(), null) }
      .toSet()
  }

  private const val RESOURCE_NAME = "roots.json"
}
