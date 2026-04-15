package paol0b.azuredevops.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.cancellation.CancellationException

/**
 * Project-level service that provides a shared [CoroutineScope] for Azure DevOps operations.
 * The scope uses a [SupervisorJob] so that failures in one coroutine don't cancel others,
 * and [Dispatchers.Default] for CPU-bound work (callers can switch to IO/Main as needed).
 *
 * The scope is automatically cancelled when the project is disposed.
 */
@Service(Service.Level.PROJECT)
class AzureDevOpsCoroutineScope : CoroutineScope, Disposable {

    private val supervisorJob = SupervisorJob()
    override val coroutineContext = Dispatchers.Default + supervisorJob

    companion object {
        fun getInstance(project: Project): AzureDevOpsCoroutineScope {
            return project.getService(AzureDevOpsCoroutineScope::class.java)
        }
    }

    override fun dispose() {
        supervisorJob.cancel(CancellationException("Project disposed"))
    }
}
