package com.womanglobal.connecther.utils

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.widget.Toast

object UIHelper {

    private var progressDialog: ProgressDialog? = null

    /**
     * Show a short toast message.
     * @param context The context where the toast should be shown.
     * @param message The message to display.
     */
    fun showToastShort(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show a long toast message.
     * @param context The context where the toast should be shown.
     * @param message The message to display.
     */
    fun showToastLong(context: Context?, message: String) {
        if (context != null){
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show an Alert Dialog with OK button.
     * @param context The context where the dialog should be shown.
     * @param title The title of the dialog.
     * @param message The message to display in the dialog.
     */
    fun showAlertDialog(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Show a Confirmation Dialog with OK & Cancel buttons.
     * @param context The context where the dialog should be shown.
     * @param title The title of the dialog.
     * @param message The message to display in the dialog.
     * @param positiveCallback The action to perform when the user clicks OK.
     */
    fun showConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        positiveCallback: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> positiveCallback() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show a Progress Dialog (Loading Spinner).
     * @param context The context where the progress dialog should be shown.
     * @param message The message to display in the progress dialog.
     */
    fun showProgressDialog(context: Context, message: String) {
        dismissProgressDialog()
        progressDialog = ProgressDialog(context).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    /**
     * Dismiss the Progress Dialog if it's showing.
     */
    fun dismissProgressDialog() {
        progressDialog?.takeIf { it.isShowing }?.dismiss()
        progressDialog = null
    }
}

