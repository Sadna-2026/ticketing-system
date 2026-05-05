
## Notifications Architecture (V1 vs V2)
As per V1 specifications (#UC-I.5 and #UC-I.6), full real-time and delayed notification delivery is **deferred to V2**.
*   **Real-Time:** Services should use the INotificationService interface in the application layer. In V1, this is bound to a no-op StubNotificationService that simply logs the intent.
*   **Delayed/Pending:** Services should reference IPendingNotificationRepository if they need to queue a message for an offline user. In V1, the StubPendingNotificationRepository sinks these calls. In V2, this will be replaced with a database-backed repository to deliver messages upon the user's next login.

## Notifications Architecture (V1 vs V2)
As per V1 specifications (#UC-I.5 and #UC-I.6), full real-time and delayed notification delivery is **deferred to V2**.
*   **Real-Time:** Services should use the INotificationService interface in the application layer. In V1, this is bound to a no-op StubNotificationService that simply logs the intent.
*   **Delayed/Pending:** Services should reference IPendingNotificationRepository if they need to queue a message for an offline user. In V1, the StubPendingNotificationRepository sinks these calls. In V2, this will be replaced with a database-backed repository to deliver messages upon the user's next login.

## Notifications Architecture (V1 vs V2)
As per V1 specifications (#UC-I.5 and #UC-I.6), full real-time and delayed notification delivery is **deferred to V2**.
*   **Real-Time:** Services should use the INotificationService interface in the application layer. In V1, this is bound to a no-op StubNotificationService that simply logs the intent.
*   **Delayed/Pending:** Services should reference IPendingNotificationRepository if they need to queue a message for an offline user. In V1, the StubPendingNotificationRepository sinks these calls. In V2, this will be replaced with a database-backed repository to deliver messages upon the user's next login.
