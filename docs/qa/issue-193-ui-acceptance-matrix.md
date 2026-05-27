# Issue #193 UI Acceptance Matrix

This matrix tracks required dual evidence per acceptance criterion:

- one **GWT-style automated test** (Given-When-Then naming),
- one **UI click-through verification** (buttons clicked and outcomes asserted).

## Criteria and evidence

1. Unauthorized route/action behavior is explicit and user-friendly.
   - GWT test:
     - `com.ticketing.presentation.vaadin.views.AdminViewTest#GivenRegularMember_WhenEnteringAdminRoute_ThenUserIsReroutedToHome`
   - UI click-through test:
     - `com.ticketing.presentation.vaadin.views.AdminViewTest#GivenRegularMemberSession_WhenRendered_ThenAdminControlsAreHiddenWithExplanation`
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenUnauthorizedApplicationResponse_WhenManagerActionClicked_ThenMessageIsDisplayed`

2. Role-based navigation/action visibility is consistent with actual permissions.
   - GWT tests:
     - `com.ticketing.presentation.vaadin.MainLayoutTest#GivenGuestSession_WhenRendered_ThenOwnerAndAdminNavigationAreHidden`
     - `com.ticketing.presentation.vaadin.MainLayoutTest#GivenMemberSession_WhenRendered_ThenCompanyNavigationIsVisibleAndAdminNavigationIsHidden`
     - `com.ticketing.presentation.vaadin.MainLayoutTest#GivenSystemAdminSession_WhenRendered_ThenAdminNavigationIsVisible`
   - UI click-through tests:
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenGuestSession_WhenRendered_ThenPublicCompanyInfoAndMapRemainVisibleButMemberActionsAreHidden`
     - `com.ticketing.presentation.vaadin.views.AdminViewTest#GivenRegularMemberSession_WhenRendered_ThenAdminControlsAreHiddenWithExplanation`

3. Policy-related UI status is accurate (implemented behavior vs placeholder scope).
   - GWT tests:
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenCompanyView_WhenRendered_ThenPublicOwnerFounderAndManagerGroupsExist`
     - `com.ticketing.presentation.vaadin.views.AdminViewTest#GivenAdminView_WhenRendered_ThenCompanyManagementControlsAreHidden`
   - UI click-through tests:
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenLifecycleAndReportingInputs_WhenActionsClicked_ThenResultsAreDisplayed`

4. Error-message behavior is consistent for invalid action inputs.
   - GWT tests:
     - `com.ticketing.presentation.vaadin.views.AdminViewTest#GivenInvalidTargetMemberId_WhenRemoveClicked_ThenInvalidIdMessageIsDisplayedBeforePresenterCall`
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenInvalidEventId_WhenPublishingEvent_ThenValidationMessageIsShownBeforePresenterCall`
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenInvalidTargetMemberId_WhenOfferingRole_ThenValidationMessageIsShownBeforePresenterCall`
   - UI click-through tests:
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenEventAndInventoryInputs_WhenActionsClicked_ThenPresenterMethodsAreCalledAndEventIdIsReused`
     - `com.ticketing.presentation.vaadin.views.CompanyViewTest#GivenPersonnelInputs_WhenRoleActionsClicked_ThenPresenterMethodsAreCalled`

## Execution notes

- Automated test command:
  - `mvn -Dtest="com.ticketing.presentation.vaadin.MainLayoutTest,com.ticketing.presentation.vaadin.views.AdminViewTest,com.ticketing.presentation.vaadin.views.CompanyViewTest,com.ticketing.presentation.vaadin.presenters.AdminPresenterTest,com.ticketing.presentation.vaadin.presenters.CompanyPresenterTest" test`
- The view tests exercise active UI behavior by setting fields and invoking `Button#click()` on Vaadin components.

## Happy and sad click-path verification set

- Auth happy path:
  - `com.ticketing.presentation.vaadin.views.AuthViewTest#GivenGuestSession_WhenLoginSucceeds_ThenOnlyLogoutIsVisible`
- Auth sad path:
  - `com.ticketing.presentation.vaadin.views.AuthViewTest#GivenGuestSession_WhenLoginFails_ThenGuestActionsRemainVisibleWithFailureMessage`
- Events happy path:
  - `com.ticketing.presentation.vaadin.views.EventsViewTest#GivenSearchReturnsEvents_WhenSearchButtonClicked_ThenResultsAreDisplayedInGrid`
  - `com.ticketing.presentation.vaadin.views.EventsViewTest#GivenSelectedEventHasMap_WhenViewMapClicked_ThenInventoryDataIsDisplayed`
- Events sad path:
  - `com.ticketing.presentation.vaadin.views.EventsViewTest#GivenApplicationError_WhenSearchButtonClicked_ThenErrorMessageIsShownToUser`
  - `com.ticketing.presentation.vaadin.views.EventsViewTest#GivenSelectedEventMapFails_WhenViewMapClicked_ThenFailureMessageIsShownInline`
- Orders happy path:
  - `com.ticketing.presentation.vaadin.views.OrdersViewTest#GivenOrderWithCoupon_WhenCheckoutClicked_ThenPurchaseIdIsDisplayed`
  - `com.ticketing.presentation.vaadin.views.OrdersViewTest#GivenSelectedOrderItem_WhenRemovingAndUpdating_ThenPresenterActionsAreCalled`
- Orders sad path:
  - `com.ticketing.presentation.vaadin.views.OrdersViewTest#GivenPolicyFailure_WhenCheckoutClicked_ThenPolicyMessageIsShownInline`
  - `com.ticketing.presentation.vaadin.views.OrdersViewTest#GivenActiveOrderAndTicketSelectionFails_WhenAddingGaTickets_ThenFailureMessageIsShownInline`
- Notifications happy path:
  - `com.ticketing.presentation.vaadin.views.NotificationsViewTest#GivenPendingNotifications_WhenRefreshClicked_ThenMessagesAreDisplayed`
  - `com.ticketing.presentation.vaadin.views.NotificationsViewTest#GivenClearSucceeds_WhenClearClicked_ThenVisibleNotificationsAreRemoved`
- Notifications sad/empty path:
  - `com.ticketing.presentation.vaadin.views.NotificationsViewTest#GivenNoPendingNotifications_WhenRefreshClicked_ThenEmptyStateIsDisplayed`
