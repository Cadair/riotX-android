/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.matrix.android.internal.database.model

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Index
import io.realm.annotations.LinkingObjects

internal open class ChunkEntity(@Index var prevToken: String? = null,
                                @Index var nextToken: String? = null,
                                var timelineEvents: RealmList<TimelineEventEntity> = RealmList(),
                                @Index var isLastForward: Boolean = false,
                                @Index var isLastBackward: Boolean = false,
                                var backwardsDisplayIndex: Int? = null,
                                var forwardsDisplayIndex: Int? = null,
                                var backwardsStateIndex: Int? = null,
                                var forwardsStateIndex: Int? = null
) : RealmObject() {

    @LinkingObjects("chunks")
    val room: RealmResults<RoomEntity>? = null

    companion object
}