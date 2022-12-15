package com.blockchain.presentation.extensions

import android.content.Intent
import android.os.Binder
import android.os.Bundle
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.TransactionTarget

fun Bundle.putAccount(key: String, account: BlockchainAccount) =
    putBinder(key, ParamBinder(account))

fun Bundle.putTarget(key: String, account: TransactionTarget) =
    putBinder(key, ParamBinder(account))

@Suppress("UNCHECKED_CAST")
fun Bundle.getAccount(key: String): BlockchainAccount? =
    (getBinder(key) as? ParamBinder<BlockchainAccount>)?.account

@Suppress("UNCHECKED_CAST")
fun Bundle.getTarget(key: String): TransactionTarget? =
    (getBinder(key) as? ParamBinder<TransactionTarget>)?.account

fun Intent.putAccount(key: String, account: BlockchainAccount) =
    this.putExtra(key, Bundle().apply { putBinder(key, ParamBinder(account)) })

@Suppress("UNCHECKED_CAST")
fun Intent.getAccount(key: String): BlockchainAccount? =
    (getBundleExtra(key)?.getBinder(key) as? ParamBinder<BlockchainAccount>)?.account

fun Intent.putSendTarget(key: String, target: TransactionTarget) =
    this.putExtra(key, Bundle().apply { putBinder(key, ParamBinder(target)) })

@Suppress("UNCHECKED_CAST")
fun Intent.getSendTarget(key: String): TransactionTarget? =
    (getBundleExtra(key)?.getBinder(key) as? ParamBinder<TransactionTarget>)?.account

private class ParamBinder<T>(
    val account: T
) : Binder()
