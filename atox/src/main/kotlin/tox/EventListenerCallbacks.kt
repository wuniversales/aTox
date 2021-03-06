package ltd.evilcorp.atox.tox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ltd.evilcorp.atox.ui.NotificationHelper
import ltd.evilcorp.core.repository.ContactRepository
import ltd.evilcorp.core.repository.FriendRequestRepository
import ltd.evilcorp.core.repository.MessageRepository
import ltd.evilcorp.core.repository.UserRepository
import ltd.evilcorp.core.vo.*
import ltd.evilcorp.domain.feature.ChatManager
import ltd.evilcorp.domain.feature.FileTransferManager
import ltd.evilcorp.domain.tox.Tox
import ltd.evilcorp.domain.tox.ToxEventListener
import ltd.evilcorp.domain.tox.toMessageType
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private fun getDate() = Date().time

@Singleton
class EventListenerCallbacks @Inject constructor(
    private val contactRepository: ContactRepository,
    private val friendRequestRepository: FriendRequestRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val chatManager: ChatManager,
    private val fileTransferManager: FileTransferManager,
    private val notificationHelper: NotificationHelper,
    private val tox: Tox
) : CoroutineScope by GlobalScope {
    private var contacts: List<Contact> = listOf()

    init {
        contactRepository.getAll().observeForever {
            contacts = it
        }
    }

    private fun contactByPublicKey(publicKey: String) =
        contacts.find { it.publicKey == publicKey }!!

    fun setUp(listener: ToxEventListener) = with(listener) {
        friendStatusMessageHandler = { publicKey, message ->
            contactRepository.setStatusMessage(publicKey, message)
        }

        friendReadReceiptHandler = { publicKey, messageId ->
            messageRepository.setReceipt(publicKey, messageId, getDate())
        }

        friendStatusHandler = { publicKey, status ->
            contactRepository.setUserStatus(publicKey, status)
        }

        friendConnectionStatusHandler = { publicKey, status ->
            contactRepository.setConnectionStatus(publicKey, status)
            if (status != ConnectionStatus.None) {
                launch {
                    val pending = messageRepository.getPending(publicKey)
                    if (pending.isNotEmpty()) {
                        chatManager.resend(pending)
                    }
                }
            }
        }

        friendRequestHandler = { publicKey, _, message ->
            FriendRequest(publicKey, message).also {
                friendRequestRepository.add(it)
                notificationHelper.showFriendRequestNotification(it)
            }
        }

        friendMessageHandler = { publicKey, type, _, msg ->
            val time = getDate()
            contactRepository.setLastMessage(publicKey, time)
            messageRepository.add(
                Message(publicKey, msg, Sender.Received, type.toMessageType(), Int.MIN_VALUE, time)
            )

            if (chatManager.activeChat != publicKey) {
                notificationHelper.showMessageNotification(contactByPublicKey(publicKey), msg)
                contactRepository.setHasUnreadMessages(publicKey, true)
            }
        }

        friendNameHandler = { publicKey, newName ->
            contactRepository.setName(publicKey, newName)
        }

        fileRecvChunkHandler = { publicKey, fileNumber, position, data ->
            fileTransferManager.addDataToTransfer(publicKey, fileNumber, position, data)
        }

        fileRecvHandler = { publicKey, fileNumber, kind, fileSize, filename ->
            val name = if (kind == FileKind.Avatar.ordinal) publicKey else filename
            fileTransferManager.add(
                FileTransfer(publicKey, fileNumber, kind, fileSize, name, outgoing = false)
            )
        }

        selfConnectionStatusHandler = { status ->
            userRepository.updateConnection(tox.publicKey.string(), status)
        }

        friendTypingHandler = { publicKey, isTyping ->
            contactRepository.setTyping(publicKey, isTyping)
        }
    }
}
