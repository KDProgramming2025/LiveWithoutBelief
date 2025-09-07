package info.lwb.auth

import android.app.Activity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Ignore

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val facade: AuthFacade = mockk()

    @Ignore("Requires main dispatcher / Robolectric; will be reworked with dispatcher injection")
    fun signInSuccessUpdatesState() = runTest {
        val user = AuthUser("id","name","email", null)
        coEvery { facade.oneTapSignIn(any()) } returns Result.success(user)
        every { facade.currentUser() } returns null
        val vm = AuthViewModel(facade)
        vm.signIn { mockk<Activity>(relaxed = true) }
        val state = vm.state.first { it is AuthUiState.SignedIn }
        assertTrue(state is AuthUiState.SignedIn)
    }
}