package piuk.blockchain.android.ui.transfer.receive.detail

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.blockchain.analytics.ProviderSpecificAnalytics
import com.blockchain.sunriver.StellarPayment
import com.blockchain.sunriver.fromStellarUri
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.isLayer2Token
import info.blockchain.wallet.util.FormatsUtil
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap
import org.bitcoinj.uri.BitcoinURI
import piuk.blockchain.android.R
import timber.log.Timber

class ReceiveDetailIntentHelper(
    private val context: Context,
    private val specificAnalytics: ProviderSpecificAnalytics
) {

    internal fun getIntentDataList(
        uri: String,
        bitmap: Bitmap,
        asset: AssetInfo
    ): List<SendPaymentCodeData> {
        val file = getQrFile()
        val outputStream = getFileOutputStream(file)

        if (outputStream != null) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream)

            try {
                outputStream.close()
            } catch (e: IOException) {
                Timber.e(e)
                return emptyList()
            }

            val dataList = ArrayList<SendPaymentCodeData>()
            val packageManager = context.packageManager
            val mime = MimeTypeMap.getSingleton()
            val ext = file.name.substring(file.name.lastIndexOf(".") + 1)
            val type = mime.getMimeTypeFromExtension(ext)

            val emailIntent = Intent(Intent.ACTION_SENDTO).apply { setupIntentForImage(type, file) }
            val displayName = asset.name

            when {
                asset.networkTicker == CryptoCurrency.BTC.networkTicker -> emailIntent.setupIntentForEmailBtc(
                    displayName,
                    uri
                )
                asset.networkTicker == CryptoCurrency.BCH.networkTicker -> emailIntent.setupIntentForEmailBch(
                    displayName,
                    uri
                )
                asset.networkTicker == CryptoCurrency.XLM.networkTicker ->
                    emailIntent.setupIntentForEmailXlm(
                        displayName = displayName,
                        payment = uri.fromStellarUri()
                    )
                asset.networkTicker == CryptoCurrency.ETHER.networkTicker ||
                    asset.isLayer2Token ->
                    emailIntent.setupIntentForEmailERC20(
                        ticker = asset.displayTicker,
                        displayName = displayName,
                        uri = uri
                    )
                else -> throw NotImplementedError("${asset.networkTicker} is not fully supported yet")
            }

            val imageIntent = Intent().apply { setupIntentForImage(type, file) }

            val intentHashMap = HashMap<String, Pair<ResolveInfo, Intent>>()

            val emailResolveInfo = packageManager.queryIntentActivities(emailIntent, 0)
            addResolveInfoToMap(emailIntent, intentHashMap, emailResolveInfo)

            val imageResolveInfo = packageManager.queryIntentActivities(imageIntent, 0)
            addResolveInfoToMap(imageIntent, intentHashMap, imageResolveInfo)

            val it = intentHashMap.entries.iterator()
            while (it.hasNext()) {
                val pair = it.next().value
                val resolveInfo = pair.first
                val context = resolveInfo.activityInfo.packageName
                val packageClassName = resolveInfo.activityInfo.name
                val label = resolveInfo.loadLabel(packageManager)
                val icon = resolveInfo.loadIcon(packageManager)

                val intent = pair.second
                intent.setClassName(context, packageClassName)

                dataList.add(SendPaymentCodeData(label.toString(), icon, intent))

                it.remove()
            }

            specificAnalytics.logShare("QR Code + URI")
            return dataList
        } else {
            return emptyList()
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun getQrFile(): File {
        val file = File(context.filesDir, "qr.png")
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        file.setReadable(true, false)
        return file
    }

    private fun getFileOutputStream(file: File): FileOutputStream? {
        return try {
            FileOutputStream(file)
        } catch (e: FileNotFoundException) {
            Timber.e(e)
            null
        }
    }

    /**
     * Prevents apps being added to the list twice, as it's confusing for users. Full email Intent
     * takes priority.
     */
    private fun addResolveInfoToMap(
        intent: Intent,
        intentHashMap: HashMap<String, Pair<ResolveInfo, Intent>>,
        resolveInfo: List<ResolveInfo>
    ) {
        resolveInfo
            .filterNot { intentHashMap.containsKey(it.activityInfo.name) }
            .forEach { intentHashMap[it.activityInfo.name] = Pair(it, Intent(intent)) }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Intent Extension functions
    // /////////////////////////////////////////////////////////////////////////
    private fun Intent.setupIntentForEmailBtc(displayName: String, uri: String) {
        val addressUri = BitcoinURI(uri)
        val amount = if (addressUri.amount != null) " " + addressUri.amount.toPlainString() else ""
        val address =
            if (addressUri.address != null) {
                addressUri.address!!.toString()
            } else {
                context.getString(com.blockchain.stringResources.R.string.email_request_body_fallback)
            }
        val body = String.format(
            context.getString(com.blockchain.stringResources.R.string.email_request_body_btc),
            amount,
            address
        )

        val text = "$body\n\n ${FormatsUtil.toBtcUri(address)}"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(
            Intent.EXTRA_SUBJECT,
            context.getString(com.blockchain.stringResources.R.string.email_request_subject, displayName)
        )
    }

    private fun Intent.setupIntentForEmailERC20(ticker: String, displayName: String, uri: String) {
        val address = uri.removePrefix("ethereum:")
        val body = String.format(
            context.getString(com.blockchain.stringResources.R.string.email_request_body_erc20),
            ticker,
            displayName,
            address
        )

        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(
            Intent.EXTRA_SUBJECT,
            context.getString(com.blockchain.stringResources.R.string.email_request_subject, displayName)
        )
    }

    private fun Intent.setupIntentForEmailXlm(displayName: String, payment: StellarPayment) {
        val body = String.format(
            context.getString(com.blockchain.stringResources.R.string.email_request_body_xlm),
            payment.public.accountId
        )

        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(
            Intent.EXTRA_SUBJECT,
            context.getString(com.blockchain.stringResources.R.string.email_request_subject, displayName)
        )
    }

    private fun Intent.setupIntentForEmailBch(displayName: String, uri: String) {
        val address = uri.removePrefix("bitcoincash:")
        val body = String.format(
            context.getString(com.blockchain.stringResources.R.string.email_request_body_bch),
            address
        )

        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(
            Intent.EXTRA_SUBJECT,
            context.getString(com.blockchain.stringResources.R.string.email_request_subject, displayName)
        )
    }

    private fun Intent.setupIntentForImage(type: String?, file: File) {
        action = Intent.ACTION_SEND
        this.type = type

        val uriForFile = FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", file)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        putExtra(Intent.EXTRA_STREAM, uriForFile)
    }
}

internal class SendPaymentCodeData(val title: String, val logo: Drawable, val intent: Intent)
