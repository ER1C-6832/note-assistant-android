package com.er1cmo.noteassistant.notes.domain.command

data class CommandRiskInput(
    val toolName: ToolName,
    val source: CommandSource = CommandSource.LocalToolSimulator,
    val affectedNoteCount: Int = 0,
    val affectedTagCount: Int = 0,
    val linkedNoteCount: Int = 0,
    val tagBindMode: TagBindMode? = null,
)

data class CommandResult(
    val status: CommandStatus,
    val message: String,
    val riskLevel: RiskLevel,
    val confirmationStatus: ConfirmationStatus = ConfirmationStatus.NotRequired,
    val commandLogId: Long? = null,
    val confirmationId: String? = null,
    val affectedNoteIds: List<Long> = emptyList(),
    val affectedTagIds: List<Long> = emptyList(),
    val resultJson: String? = null,
    val errorCode: CommandErrorCode? = null,
) {
    val isSuccess: Boolean get() = status == CommandStatus.Success || status == CommandStatus.PartialSuccess

    companion object {
        fun success(
            message: String,
            riskLevel: RiskLevel,
            commandLogId: Long? = null,
            affectedNoteIds: List<Long> = emptyList(),
            affectedTagIds: List<Long> = emptyList(),
            resultJson: String? = null,
        ): CommandResult = CommandResult(
            status = CommandStatus.Success,
            message = message,
            riskLevel = riskLevel,
            commandLogId = commandLogId,
            affectedNoteIds = affectedNoteIds,
            affectedTagIds = affectedTagIds,
            resultJson = resultJson,
        )

        fun failure(
            message: String,
            riskLevel: RiskLevel = RiskLevel.Low,
            errorCode: CommandErrorCode = CommandErrorCode.ValidationError,
            commandLogId: Long? = null,
        ): CommandResult = CommandResult(
            status = CommandStatus.Failed,
            message = message,
            riskLevel = riskLevel,
            commandLogId = commandLogId,
            errorCode = errorCode,
        )

        fun requiresConfirmation(
            message: String,
            commandLogId: Long,
            confirmationId: String,
            affectedNoteIds: List<Long> = emptyList(),
            affectedTagIds: List<Long> = emptyList(),
            resultJson: String? = null,
        ): CommandResult = CommandResult(
            status = CommandStatus.RequiresConfirmation,
            message = message,
            riskLevel = RiskLevel.High,
            confirmationStatus = ConfirmationStatus.Pending,
            commandLogId = commandLogId,
            confirmationId = confirmationId,
            affectedNoteIds = affectedNoteIds,
            affectedTagIds = affectedTagIds,
            resultJson = resultJson,
            errorCode = CommandErrorCode.RequiresConfirmation,
        )
    }
}
