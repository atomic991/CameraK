package org.company.app


import androidx.lifecycle.ViewModel
import com.kashif.invoicescannerplugin.InvoiceScannerData
import kotlinx.datetime.LocalDate
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class CameraViewModel(): ViewModel(),
    ContainerHost<CameraViewModel.CameraState, CameraViewModel.CameraSideEffects> {

    override val container: Container<CameraState, CameraSideEffects> = container(CameraState())

    fun setData(data: InvoiceScannerData) = intent {
        reduce { state.copy(date = data.date, dueDate = data.dueDate) }
//        postSideEffect(CameraSideEffects.OpenInvoiceDetails(newInvoice))
    }

    data class CameraState(
        val date: LocalDate? = null,
        val dueDate: LocalDate? = null
    )

    sealed class CameraSideEffects {
    }
}