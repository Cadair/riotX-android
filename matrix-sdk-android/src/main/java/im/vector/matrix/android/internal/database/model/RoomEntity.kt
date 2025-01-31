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

import im.vector.matrix.android.api.session.room.model.Membership
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import kotlin.properties.Delegates

internal open class RoomEntity(@PrimaryKey var roomId: String = "",
                               var chunks: RealmList<ChunkEntity> = RealmList(),
                               var untimelinedStateEvents: RealmList<EventEntity> = RealmList(),
                               var sendingTimelineEvents: RealmList<TimelineEventEntity> = RealmList(),
                               var areAllMembersLoaded: Boolean = false
) : RealmObject() {

    private var membershipStr: String = Membership.NONE.name

    @delegate:Ignore
    var membership: Membership by Delegates.observable(Membership.valueOf(membershipStr)) { _, _, newValue ->
        membershipStr = newValue.name
    }

    companion object
}

