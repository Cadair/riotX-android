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
package im.vector.riotx.features.home.room.detail.timeline.action

import com.airbnb.mvrx.*
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import im.vector.matrix.android.api.session.Session
import im.vector.matrix.android.api.session.events.model.EventType
import im.vector.matrix.android.api.session.events.model.toModel
import im.vector.matrix.android.api.session.room.model.message.MessageContent
import im.vector.matrix.android.api.session.room.model.message.MessageImageContent
import im.vector.matrix.android.api.session.room.model.message.MessageType
import im.vector.matrix.android.api.session.room.send.SendState
import im.vector.matrix.android.api.session.room.timeline.TimelineEvent
import im.vector.matrix.rx.RxRoom
import im.vector.riotx.R
import im.vector.riotx.core.extensions.canReact
import im.vector.riotx.core.platform.VectorViewModel
import im.vector.riotx.core.resources.StringProvider
import im.vector.riotx.features.home.room.detail.timeline.item.MessageInformationData


data class SimpleAction(val uid: String, val titleRes: Int, val iconResId: Int?, val data: Any? = null)

data class MessageMenuState(
        val roomId: String,
        val eventId: String,
        val informationData: MessageInformationData,
        val actions: Async<List<SimpleAction>> = Uninitialized
) : MvRxState {

    constructor(args: TimelineEventFragmentArgs) : this(roomId = args.roomId, eventId = args.eventId, informationData = args.informationData)

}

/**
 * Manages list actions for a given message (copy / paste / forward...)
 */
class MessageMenuViewModel @AssistedInject constructor(@Assisted initialState: MessageMenuState,
                                                       private val session: Session,
                                                       private val stringProvider: StringProvider) : VectorViewModel<MessageMenuState>(initialState) {

    @AssistedInject.Factory
    interface Factory {
        fun create(initialState: MessageMenuState): MessageMenuViewModel
    }

    private val room = session.getRoom(initialState.roomId)
            ?: throw IllegalStateException("Shouldn't use this ViewModel without a room")

    private val eventId = initialState.eventId
    private val informationData: MessageInformationData = initialState.informationData

    companion object : MvRxViewModelFactory<MessageMenuViewModel, MessageMenuState> {

        const val ACTION_ADD_REACTION = "add_reaction"
        const val ACTION_COPY = "copy"
        const val ACTION_EDIT = "edit"
        const val ACTION_QUOTE = "quote"
        const val ACTION_REPLY = "reply"
        const val ACTION_SHARE = "share"
        const val ACTION_RESEND = "resend"
        const val ACTION_DELETE = "delete"
        const val VIEW_SOURCE = "VIEW_SOURCE"
        const val VIEW_DECRYPTED_SOURCE = "VIEW_DECRYPTED_SOURCE"
        const val ACTION_COPY_PERMALINK = "ACTION_COPY_PERMALINK"
        const val ACTION_FLAG = "ACTION_FLAG"
        const val ACTION_QUICK_REACT = "ACTION_QUICK_REACT"
        const val ACTION_VIEW_REACTIONS = "ACTION_VIEW_REACTIONS"

        override fun create(viewModelContext: ViewModelContext, state: MessageMenuState): MessageMenuViewModel? {
            val fragment: MessageMenuFragment = (viewModelContext as FragmentViewModelContext).fragment()
            return fragment.messageMenuViewModelFactory.create(state)
        }
    }

    init {
        observeEvent()
    }

    private fun observeEvent() {
        RxRoom(room)
                .liveTimelineEvent(eventId)
                ?.map {
                    actionsForEvent(it)
                }
                ?.execute {
                    copy(actions = it)
                }
    }

    private fun actionsForEvent(event: TimelineEvent): List<SimpleAction> {

        val messageContent: MessageContent? = event.annotations?.editSummary?.aggregatedContent.toModel()
                ?: event.root.getClearContent().toModel()
        val type = messageContent?.type

        val actions = if (!event.sendState.isSent()) {
            //Resend and Delete
            listOf<SimpleAction>(
//                                SimpleAction(ACTION_RESEND, R.string.resend, R.drawable.ic_send, event.root.eventId),
//                                //TODO delete icon
//                                SimpleAction(ACTION_DELETE, R.string.delete, R.drawable.ic_delete, event.root.eventId)
            )
        } else {
            arrayListOf<SimpleAction>().apply {

                if (event.sendState == SendState.SENDING) {
                    //TODO add cancel?
                    return@apply
                }
                //TODO is downloading attachement?

                if (event.canReact()) {
                    this.add(SimpleAction(ACTION_ADD_REACTION, R.string.message_add_reaction, R.drawable.ic_add_reaction, eventId))
                }
                if (canCopy(type)) {
                    //TODO copy images? html? see ClipBoard
                    this.add(SimpleAction(ACTION_COPY, R.string.copy, R.drawable.ic_copy, messageContent!!.body))
                }

                if (canReply(event, messageContent)) {
                    this.add(SimpleAction(ACTION_REPLY, R.string.reply, R.drawable.ic_reply, eventId))
                }

                if (canEdit(event, session.sessionParams.credentials.userId)) {
                    this.add(SimpleAction(ACTION_EDIT, R.string.edit, R.drawable.ic_edit, eventId))
                }

                if (canRedact(event, session.sessionParams.credentials.userId)) {
                    this.add(SimpleAction(ACTION_DELETE, R.string.delete, R.drawable.ic_delete, eventId))
                }

                if (canQuote(event, messageContent)) {
                    this.add(SimpleAction(ACTION_QUOTE, R.string.quote, R.drawable.ic_quote, eventId))
                }

                if (canViewReactions(event)) {
                    this.add(SimpleAction(ACTION_VIEW_REACTIONS, R.string.message_view_reaction, R.drawable.ic_view_reactions, informationData))
                }

                if (canShare(type)) {
                    if (messageContent is MessageImageContent) {
                        this.add(
                                SimpleAction(ACTION_SHARE,
                                        R.string.share, R.drawable.ic_share,
                                        session.contentUrlResolver().resolveFullSize(messageContent.url))
                        )
                    }
                    //TODO
                }


                if (event.sendState == SendState.SENT) {

                    //TODO Can be redacted

                    //TODO sent by me or sufficient power level
                }

                this.add(SimpleAction(VIEW_SOURCE, R.string.view_source, R.drawable.ic_view_source, event.root.toContentStringWithIndent()))
                if (event.isEncrypted()) {
                    val decryptedContent = event.root.toClearContentStringWithIndent()
                            ?: stringProvider.getString(R.string.encryption_information_decryption_error)
                    this.add(SimpleAction(VIEW_DECRYPTED_SOURCE, R.string.view_decrypted_source, R.drawable.ic_view_source, decryptedContent))
                }
                this.add(SimpleAction(ACTION_COPY_PERMALINK, R.string.permalink, R.drawable.ic_permalink, event.root.eventId))

                if (session.sessionParams.credentials.userId != event.root.senderId && event.root.getClearType() == EventType.MESSAGE) {
                    //not sent by me
                    this.add(SimpleAction(ACTION_FLAG, R.string.report_content, R.drawable.ic_flag, event.root.eventId))
                }
            }
        }
        return actions
    }


    private fun canReply(event: TimelineEvent, messageContent: MessageContent?): Boolean {
        //Only event of type Event.EVENT_TYPE_MESSAGE are supported for the moment
        if (event.root.getClearType() != EventType.MESSAGE) return false
        return when (messageContent?.type) {
            MessageType.MSGTYPE_TEXT,
            MessageType.MSGTYPE_NOTICE,
            MessageType.MSGTYPE_EMOTE,
            MessageType.MSGTYPE_IMAGE,
            MessageType.MSGTYPE_VIDEO,
            MessageType.MSGTYPE_AUDIO,
            MessageType.MSGTYPE_FILE -> true
            else                     -> false
        }
    }

    private fun canQuote(event: TimelineEvent, messageContent: MessageContent?): Boolean {
        //Only event of type Event.EVENT_TYPE_MESSAGE are supported for the moment
        if (event.root.getClearType() != EventType.MESSAGE) return false
        return when (messageContent?.type) {
            MessageType.MSGTYPE_TEXT,
            MessageType.MSGTYPE_NOTICE,
            MessageType.MSGTYPE_EMOTE,
            MessageType.FORMAT_MATRIX_HTML,
            MessageType.MSGTYPE_LOCATION -> {
                true
            }
            else                         -> false
        }
    }

    private fun canRedact(event: TimelineEvent, myUserId: String): Boolean {
        //Only event of type Event.EVENT_TYPE_MESSAGE are supported for the moment
        if (event.root.getClearType() != EventType.MESSAGE) return false
        //TODO if user is admin or moderator
        return event.root.senderId == myUserId
    }

    private fun canViewReactions(event: TimelineEvent): Boolean {
        //Only event of type Event.EVENT_TYPE_MESSAGE are supported for the moment
        if (event.root.getClearType() != EventType.MESSAGE) return false
        //TODO if user is admin or moderator
        return event.annotations?.reactionsSummary?.isNotEmpty() ?: false
    }


    private fun canEdit(event: TimelineEvent, myUserId: String): Boolean {
        //Only event of type Event.EVENT_TYPE_MESSAGE are supported for the moment
        if (event.root.getClearType() != EventType.MESSAGE) return false
        //TODO if user is admin or moderator
        val messageContent = event.root.content.toModel<MessageContent>()
        return event.root.senderId == myUserId && (
                messageContent?.type == MessageType.MSGTYPE_TEXT
                        || messageContent?.type == MessageType.MSGTYPE_EMOTE
                )
    }


    private fun canCopy(type: String?): Boolean {
        return when (type) {
            MessageType.MSGTYPE_TEXT,
            MessageType.MSGTYPE_NOTICE,
            MessageType.MSGTYPE_EMOTE,
            MessageType.FORMAT_MATRIX_HTML,
            MessageType.MSGTYPE_LOCATION -> {
                true
            }
            else                         -> false
        }
    }


    private fun canShare(type: String?): Boolean {
        return when (type) {
            MessageType.MSGTYPE_IMAGE,
            MessageType.MSGTYPE_AUDIO,
            MessageType.MSGTYPE_VIDEO -> {
                true
            }
            else                      -> false
        }
    }
}
