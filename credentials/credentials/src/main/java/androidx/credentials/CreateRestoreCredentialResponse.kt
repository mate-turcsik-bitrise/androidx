/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.credentials

import android.os.Bundle
import androidx.credentials.exceptions.CreateCredentialUnknownException

/**
 * A response of the [RestoreCredential] flow.
 *
 * @property responseJson the public key credential registration response in JSON format.
 */
class CreateRestoreCredentialResponse(
    val responseJson: String,
    data: Bundle,
) : CreateCredentialResponse(RestoreCredential.TYPE_RESTORE_CREDENTIAL, data) {
    companion object {
        const val BUNDLE_KEY_CREATE_RESTORE_CREDENTIAL_RESPONSE =
            "androidx.credentials.BUNDLE_KEY_CREATE_RESTORE_CREDENTIAL_RESPONSE"

        @JvmStatic
        fun createFrom(data: Bundle): CreateRestoreCredentialResponse {
            val responseJson =
                data.getString(BUNDLE_KEY_CREATE_RESTORE_CREDENTIAL_RESPONSE)
                    ?: throw CreateCredentialUnknownException(
                        "The response bundle did not contain the response data. This should not happen."
                    )
            return CreateRestoreCredentialResponse(responseJson, data)
        }
    }
}
