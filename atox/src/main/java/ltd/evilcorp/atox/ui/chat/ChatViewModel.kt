package ltd.evilcorp.atox.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ltd.evilcorp.atox.feature.ChatManager
import ltd.evilcorp.atox.feature.ContactManager
import ltd.evilcorp.atox.tox.PublicKey
import ltd.evilcorp.atox.ui.NotificationHelper
import ltd.evilcorp.core.vo.Contact
import ltd.evilcorp.core.vo.Message
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    private val chatManager: ChatManager,
    private val contactManager: ContactManager,
    private val notificationHelper: NotificationHelper
) : ViewModel() {
    private var publicKey = PublicKey("")
    private var sentTyping = false

    val contact: LiveData<Contact> by lazy { contactManager.get(publicKey) }
    val messages: LiveData<List<Message>> by lazy { chatManager.messagesFor(publicKey) }

    fun sendMessage(message: String) = chatManager.sendMessage(publicKey, message)
    fun clearHistory() = chatManager.clearHistory(publicKey)
    fun setActiveChat(pubKey: PublicKey) {
        if (pubKey.string().isEmpty()) {
            setTyping(false)
        }

        publicKey = pubKey
        notificationHelper.activeChat = pubKey.string()
    }

    fun setTyping(typing: Boolean) {
        if (publicKey.string().isEmpty()) return
        if (sentTyping != typing) {
            chatManager.setTyping(publicKey, typing)
            sentTyping = typing
        }
    }
}