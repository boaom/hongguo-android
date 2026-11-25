package com.hongguo.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hongguo.account.protocol.RelatedWork
import com.hongguo.android.DramaCard
import com.hongguo.android.HongguoApplication
import com.hongguo.android.LibraryEntry

/** UI recreation does not create a new login session or resend a verification code. */
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as HongguoApplication).accountRepository
    val state = repository.state
    val ownerKey: String? get() = repository.ownerKey
    val sessionKey: String get() = repository.sessionKey
    fun startQrLogin() = repository.startQrLogin()
    fun cancelLogin() = repository.cancelLogin()
    fun sendSms(phone: String) = repository.sendSms(phone)
    fun verifySms(code: String) = repository.verifySms(code)
    fun importCookie(cookie: String) = repository.importCookie(cookie)

    /** 当前登录 Cookie（可能为空），供播放通道在登录态下取完整分集。 */
    fun websiteCookie(): String = repository.websiteCookie()

    /** WEBSITE / APP / NONE：让用户知道播放通道用的是哪种登录态。 */
    fun websiteCookieSource(): String = repository.websiteCookieSource()
    fun importAppCookie(cookie: String) = repository.importAppCookie(cookie)
    fun completeWebLogin(cookie: String) = repository.completeWebLogin(cookie)
    fun retryWebsiteAccount() = repository.retryWebsiteAccount()
    fun logout() = repository.logout()
    fun refresh() = repository.refresh()
    fun setSyncEnabled(enabled: Boolean) = repository.setSyncEnabled(enabled)
    fun setFavorite(card: DramaCard, add: Boolean, expectedOwner: String? = ownerKey) = repository.setFavorite(card, add, expectedOwner)
    fun resolveFavorite(seriesId: String) = repository.resolveFavorite(seriesId)
    fun reserve(work: RelatedWork, expectedOwner: String? = ownerKey) = repository.reserve(work, expectedOwner)
    fun selectSection(section: AccountSection) = repository.selectSection(section)
    fun openReservations() {
        repository.selectSection(AccountSection.RESERVATIONS)
        repository.refresh()
    }
    fun consumeMessage() = repository.consumeMessage()
    fun consumeReservationResult(result: ReservationResult) = repository.consumeReservationResult(result)
    fun reportProgress(entry: LibraryEntry, durationMs: Long, force: Boolean = false, ownerKey: String? = this.ownerKey) =
        repository.reportProgress(entry, durationMs, force, ownerKey)
    fun flushProgress(ownerKey: String?) = repository.flushProgress(ownerKey)
}
