# LLM Usage Log

## Feature / Component: Issue #36 (UC-II.3.2 — Open production company) - Merge Conflict Resolution & Service Refactoring

Purpose of LLM use: Assisted in resolving merge conflicts between the issue-36 feature branch and develop branch, then refactored CompanyService to align with the official StaffAppointment model from INF-8 specification.

Summary of prompt(s): 
1. "Help me solve the merge conflicts while striving to make sure any and all of my changes are saved and left in, as well as any functionality that others added."
2. Requested guidance on resolving architectural incompatibility between two different Member models.
3. After confirming to use develop's Member model (per INF-8), asked to refactor CompanyService accordingly.

Output received (short description): 
- Detailed merge conflict analysis identifying 11 conflict categories (5 modify/delete conflicts, 6 file location conflicts)
- Strategic decision matrix showing why develop's Member model (using StaffAppointment) aligns with INF-8 specification
- Refactored CompanyService code replacing Producer role hierarchy with StaffAppointment model
- Updated imports and test mock behavior to handle Optional<Member> return types
- Compile-verified solution with all 97 source files successfully compiling

Files / components affected:
- src/main/java/com/ticketing/application/CompanyService.java
- src/main/java/com/ticketing/domain/company/Company.java
- src/main/java/com/ticketing/domain/company/ICompanyRepository.java
- src/main/java/com/ticketing/domain/member/IMemberRepository.java
- src/main/java/com/ticketing/domain/member/{ContactInfo, Founder, Manager, Owner, Producer, ProducerRole, RoleFactory}.java (package declarations updated)
- src/test/java/com/ticketing/application/CompanyServiceTest.java

Modifications made:
- Resolved 3 modify/delete conflicts by keeping CompanyService and Company implementations (new work) while accepting develop's deletions of old placeholder stubs
- Moved 7 role-related classes from user/ to member/ directory and updated all package declarations to com.ticketing.domain.member
- Refactored CompanyService.openProductionCompany() to create StaffAppointment(companyName, founderId, StaffRole.OWNER, empty permissions) instead of using Producer role hierarchy
- Created IMemberRepository interface with standard methods: findById (Optional), save, findByUsername (Optional), findByEmail (Optional), existsByUsername, existsByEmail
- Updated CompanyServiceTest mocks to return Optional<Member> and provide valid Member constructor arguments (username, email, encrypted password)
- Updated all package imports throughout codebase from com.ticketing.domain.user to com.ticketing.domain.member

Initial gaps in understanding (if any): 

Final understanding (brief explanation in your own words): Better understanding of decided repository structure.




## Feature / Component: Issue #41 (UC-G4.1 — Create event with inventory and venue map) - Code review polish

Purpose of LLM use: After implementing EventService.createEvent and the VenueMap value object, asked the LLM to review the code and tests for missed edge cases and for adherence to the project's testing conventions.

Summary of prompt(s):
1. "Look at my VenueMap and Event.setVenueMap — am I missing any edge cases on the zones-vs-sections check?"
2. "Read 2-Model-implementation.pdf and confirm my test naming follows the course convention."
3. "Help trim the javadocs on the new files to match the existing codebase style."
4. Post merge, how does removal of company UUID effect the code

Output received (short description):
- Pointed out that I was only checking one direction of the bijection (every section maps to a real zone) and was missing the reverse (every zone must be referenced by at least one section); I added the second check to Event.setVenueMap.
- Confirmed my test method names should follow Given<Condition>_When<Method>_Then<Result>
- Suggested trimming the verbose javadoc blocks on VenueMap/Event/EventService to match the minimal style used in Company.java and InventoryZone.java.
- Refactored usage of companyID to companyName

Files / components affected:
- src/main/java/com/ticketing/domain/event/Event.java (reverse-direction bijection check added; trimmed comments)
- src/main/java/com/ticketing/domain/event/VenueMap.java (trimmed comments)
- src/main/java/com/ticketing/application/EventService.java (trimmed comments)
- src/main/java/com/ticketing/application/CreateEventRequest.java (trimmed comments)
- src/test/java/com/ticketing/application/EventServiceCreateEventTest.java (renamed test methods to Given_When_Then; removed @DisplayName)

Modifications made:
- Added the reverse bijection check in Event.setVenueMap (every event zone must be referenced by at least one VenueMap section).
- Renamed all 19 test methods in EventServiceCreateEventTest to the Given_When_Then format.
- Removed @DisplayName annotations and trimmed class-level / method-level javadocs across the new files to match the existing codebase style.

Initial gaps in understanding (if any):

Final understanding (brief explanation in your own words): understood zoneMap edge cases better and naming/documenting conventions.
## Feature / Component: Issue #9 (INF-8 — ManagerPermission enum + role-based check in domain)

Purpose of LLM use: Assisted in implementing role-based permission checks within the Company domain entity and creating a comprehensive test suite to verify the access control logic.

Summary of prompt(s):
1. "How should we implement the permission enum for INF-8?"
2. "Help me implement the checkPermission logic in Company.java, but keep it strictly in the domain layer."
3. "Can we add some methods like editPolicy and viewReports that use this logic?"
4. "Let's create a test suite that covers owner implicit permissions and manager explicit ones."

Output received (short description):
- Guidance on enum structure and domain-level exception handling.
- Collaborative implementation of permission checks in `Company.java`.
- Iterative creation of `CompanyPermissionTest.java` to verify all role behaviors.

Files / components affected:
- src/main/java/com/ticketing/domain/company/Company.java
- src/test/java/com/ticketing/domain/company/CompanyPermissionTest.java

Modifications made:
- Iteratively added `checkPermission` logic to `Company`.
- Wired various domain methods with permission-based safeguards.
- Built a test suite to ensure robust RBAC across different staff roles.

Initial gaps in understanding (if any): 

Final understanding (brief explanation in your own words): Role-based access control should be enforced directly within domain entities.

## Feature / Component: Issue #43 (UC-G4.6 — Cancel/delete existing event) - Edge case + style consult

Purpose of LLM use: After implementing Event.cancel() and EventService.cancelEvent(), asked the LLM to sanity-check the auth choice and confirm the test naming/structure matches the rest of the codebase.

Summary of prompt(s):
1. "For cancelEvent, should I require the company to be ACTIVE like createEvent does, or allow cancel as a cleanup operation even when the company is suspended?"
2. "Quick read of my permission check — appointment.hasPermission(EVENT_LIFECYCLE) works for both Owner and Manager because hasPermission short-circuits to true for Owner, right?"
3. "The 'Guests cannot create events' message in authenticateMember is now misleading for cancelEvent. Should I genericize it?"

Output received (short description):
- Recommended NOT requiring company to be ACTIVE for cancel — argued cancel is a cleanup operation and UC-C7's permanent-close will need to cancel events while the company is in a transient state.
- Confirmed StaffAppointment.hasPermission already short-circuits to true for Owners (saw it in the source), so the single-line permission check is correct.
- Suggested genericizing the authenticateMember message to "Guests cannot perform this action" since it's shared across the service's methods.

Files / components affected:
- src/main/java/com/ticketing/domain/event/Event.java (added cancel() with already-cancelled guard)
- src/main/java/com/ticketing/application/EventService.java (added cancelEvent(); generalized guest-token error message)
- src/test/java/com/ticketing/application/EventServiceCancelEventTest.java (new — 9 acceptance + boundary tests)

Modifications made:
- Added Event.cancel() that throws IllegalStateException if the event is already cancelled.
- Added EventService.cancelEvent(token, eventId) using an inline permission check (instead of the extracted authorize() helper used by createEvent — the cancel rule is a single permission so an inline check reads cleaner).
- Generalized the "Guests cannot create events" message to "Guests cannot perform this action" since authenticateMember is shared.
- Added a TODO inside cancelEvent for the refund-completed-purchases pathway, deferred until the completed-purchase repository is wired up.

Initial gaps in understanding (if any):

Final understanding (brief explanation in your own words):
