/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.matrix.android.internal.crypto.model

import im.vector.matrix.android.api.util.JsonDict
import timber.log.Timber
import java.util.*

data class MXKey(
        /**
         * The type of the key (in the example: "signed_curve25519").
         */
        val type: String,

        /**
         * The id of the key (in the example: "AAAAFw").
         */
        private val keyId: String,

        /**
         * The key (in the example: "IjwIcskng7YjYcn0tS8TUOT2OHHtBSfMpcfIczCgXj4").
         */
        val value: String,

        /**
         * signature user Id to [deviceid][signature]
         */
        private val signatures: Map<String, Map<String, String>>
) {

    /**
     * @return the signed data map
     */
    fun signalableJSONDictionary(): Map<String, Any> {
        val map = HashMap<String, Any>()

        map["key"] = value

        return map
    }

    /**
     * Returns a signature for an user Id and a signkey
     *
     * @param userId  the user id
     * @param signkey the sign key
     * @return the signature
     */
    fun signatureForUserId(userId: String, signkey: String): String? {
        // sanity checks
        if (userId.isNotBlank() && signkey.isNotBlank()) {
            if (signatures.containsKey(userId)) {
                return signatures[userId]?.get(signkey)
            }
        }

        return null
    }


    companion object {
        /**
         * Key types.
         */
        const val KEY_CURVE_25519_TYPE = "curve25519"
        const val KEY_SIGNED_CURVE_25519_TYPE = "signed_curve25519"
        // const val KEY_ED_25519_TYPE = "ed25519"

        /**
         * Convert a map to a MXKey
         *
         * @param map the map to convert
         *
         * Json Example:
         *
         * <pre>
         *   "signed_curve25519:AAAAFw": {
         *     "key": "IjwIcskng7YjYcn0tS8TUOT2OHHtBSfMpcfIczCgXj4",
         *     "signatures": {
         *       "@userId:matrix.org": {
         *         "ed25519:GMJRREOASV": "EUjp6pXzK9u3SDFR\/qLbzpOi3bEREeI6qMnKzXu992HsfuDDZftfJfiUXv9b\/Hqq1og4qM\/vCQJGTHAWMmgkCg"
         *       }
         *     }
         *   }
         * </pre>
         *
         * into several val members
         */
        fun from(map: Map<String, JsonDict>?): MXKey? {
            if (map?.isNotEmpty() == true) {
                val firstKey = map.keys.first()

                val components = firstKey.split(":").dropLastWhile { it.isEmpty() }

                if (components.size == 2) {
                    val params = map[firstKey]
                    if (params != null) {
                        if (params["key"] is String) {
                            return MXKey(
                                    type = components[0],
                                    keyId = components[1],
                                    value = params["key"] as String,
                                    signatures = params["signatures"] as Map<String, Map<String, String>>
                            )
                        }
                    }
                }
            }

            // Error case
            Timber.e("## Unable to parse map")
            return null
        }
    }
}