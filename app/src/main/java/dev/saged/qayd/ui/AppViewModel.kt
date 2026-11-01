package dev.saged.qayd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.NewShop
import dev.saged.qayd.data.QaydError
import dev.saged.qayd.data.QaydRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class Active(val account: Account) : Session
}

/** حالة عملية تنتظر الشبكة: انشغال، خطأ، أو رسالة نجاح. */
data class Op(val busy: Boolean = false, val error: String? = null, val done: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = QaydRepo(app)

    val session: StateFlow<Session> = combine(repo.ready, repo.account) { ready, acc ->
        when {
            !ready -> Session.Loading
            acc == null -> Session.SignedOut
            else -> Session.Active(acc)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)

    val login = MutableStateFlow(Op())
    val adminOp = MutableStateFlow(Op())

    fun signIn(user: String, pass: String) = run(login) { repo.signIn(user, pass); null }

    fun signOut() = repo.signOut()

    fun createShop(s: NewShop) = run(adminOp) { "تم إنشاء المحل. دخول المالك: ${repo.createShop(s)}" }

    fun createStaff(username: String, name: String, pass: String) =
        run(adminOp) { "تمت إضافة الموظف. اسم الدخول: ${repo.createStaff(username, name, pass)}" }

    fun clearOp() { adminOp.value = Op() }

    private fun run(state: MutableStateFlow<Op>, block: suspend () -> String?) {
        if (state.value.busy) return
        state.value = Op(busy = true)
        viewModelScope.launch {
            state.value = try {
                Op(done = block())
            } catch (e: QaydError) {
                Op(error = e.message)
            } catch (e: Exception) {
                Op(error = "تعذر إتمام العملية، حاول مرة أخرى")
            }
        }
    }
}
