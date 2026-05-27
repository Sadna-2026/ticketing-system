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

## Feature / Component: INF-13 — Race-condition test suite

Purpose of LLM use: Assisted in debugging synchronization issues in the Member aggregate and provided guidance on using CountDownLatch for precise concurrency testing.

Summary of prompt(s):
1. "How can I ensure all threads in my ExecutorService start at the exact same moment to maximize contention?"

Output received (short description):
- Recommended using `CountDownLatch` to synchronize the start of all test threads.

Files / components affected:
- src/test/java/com/ticketing/concurrency/GlobalRaceConditionTest.java
- src/main/java/com/ticketing/domain/member/Member.java

### Modifications made:
- Refined the synchronization logic in `Member.java` based on AI suggestions to prevent `ConcurrentModificationException`.
- Integrated `CountDownLatch` into the test suite to ensure robust race-condition detection.

### Initial gaps in understanding (if any):
- Complexity of ensuring simultaneous thread execution in Java's memory model.

### Final understanding:
- Using low-level synchronization primitives like `CountDownLatch` is essential for creating reliable race-condition tests, and even thread-safe collections (like `Hashtable`) don't protect the entire aggregate without proper synchronization of composite operations.

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

Final understanding (brief explanation in your own words): Cancellation should be permitted even for suspended companies to allow for graceful cleanup, but guards should distinguish between structural and editorial changes.


## Feature / Component: Issue #42 (UC-G4.2 — Edit core event details) - CR fixes
Purpose of LLM use: Two CR fixes — perf query + clarifying comment on the two domain guards.
Summary of prompt(s):
1. "Reviewer wants findByEventId on IActiveOrderRepository instead of scanning findAllActive — apply."
2. "Reviewer flagged that addZone uses a DRAFT-only guard while setName uses a not-CANCELLED guard. Are these supposed to differ?"
Output received (short description):
- Added findActiveByEventId(UUID) to the order repo and used it in EventService.
- Confirmed the two guards differ on purpose (structural vs editorial mutations) and added a short comment near them.
Files / components affected:
- src/main/java/com/ticketing/infrastructure/Interface/IActiveOrderRepository.java
- src/main/java/com/ticketing/infrastructure/InMemoryActiveOrderRepository.java
- src/main/java/com/ticketing/application/EventService.java (use new query, drop unused import)
- src/main/java/com/ticketing/domain/event/Event.java (comment near the two guards)
- src/test/java/com/ticketing/application/EventServiceEditEventTest.java (mock new query)
Modifications made:
- O(1)-keyed query replaces O(N) scan in hasActiveReservations.
- One-block comment makes the structural-vs-editorial split explicit; no rename.
Initial gaps in understanding (if any):
Final understanding (brief explanation in your own words): High-performance lookups should be prioritized for frequently checked invariants (like active reservations) by adding targeted repository queries.

## Feature / Component: Issue #45 (UC-C.1 — Manage event layout & inventory) - Concurrency strategy
Purpose of LLM use: Quick consult on the locking strategy for inventory mutations and the V1 §6.a race-test shape.
Summary of prompt(s):
1. "Service-level synchronized vs per-event lock vs CAS — what's V1-correct given InMemoryEventRepository's CAS doesn't fire under in-memory aliasing?"
2. "Race test: two threads adding seats vs two threads removing the same seat — which gives a sharper signal?"
Output received (short description):
- Service-level synchronized is enough for V1; CAS is unreliable under aliasing.
- Both race tests are useful — concurrent-add proves no lost updates, concurrent-same-removal proves single-success semantics.
Files / components affected:
- src/main/java/com/ticketing/domain/event/InventoryZone.java (removeSeat, increase/decreaseCapacity, guarded setPricePerTicket)
- src/main/java/com/ticketing/application/EventService.java (5 synchronized inventory methods + authorizeInventory + loadEventForInventoryEdit)
- src/test/java/com/ticketing/application/EventServiceInventoryTest.java (new — 13 tests incl. 2 race tests)
Modifications made:
- New domain ops on InventoryZone with state-machine guards.
- Permission is INVENTORY_MGMT OR MAP_DEFINITION (not both).
- Race tests use ExecutorService + CountDownLatch per V1 §6.a.
Initial gaps in understanding (if any):
Final understanding (brief explanation in your own words):

## Feature / Component: Issue #28 (UC-II.6 — View venue map and inventory) - DTO + visibility consult
Purpose of LLM use: Quick consult on which event statuses to expose and how to shape the live-availability DTO.
Summary of prompt(s):
1. "Cancelled events — return DTO with status=CANCELLED, or hide entirely?"
2. "ZoneInfo with GA-only and Assigned-only fields — single record with nullable fields, or polymorphic?"
Output received (short description):
- Hide cancelled (matches CompanyQueryService pattern); only PUBLISHED + SOLD_OUT browsable.
- Single record with nullable GA counters / nullable seats list — simpler for V1 than a sealed hierarchy.
Files / components affected:
- src/main/java/com/ticketing/application/EventMapDTO.java (new — nested ZoneInfo + SeatInfo records, defensive copies)
- src/main/java/com/ticketing/application/EventQueryService.java (new — token-less)
- src/test/java/com/ticketing/application/EventQueryServiceTest.java (new — 9 tests)
Modifications made:
- Token-less getEventMap returning Optional.empty for unknown / DRAFT / CANCELLED.
- Snapshot semantics + immutable lists baked into the DTO.
Initial gaps in understanding (if any):
Final understanding (brief explanation in your own words):

## Feature / Component: Issue #39 (UC-II.3.6 — Register for purchase-right lottery) - Implementation & tests

Purpose of LLM use: Assisted in implementing the lottery registration use case — domain model extensions and acceptance tests.

Summary of prompt(s):
1. Help unite LotteryRegistration inside Event.
2. Design the plan and explain what needs to be added.

Output received (short description):
- Implementation plan identifying existing lottery infrastructure (LotteryEntry, ILotteryRepository) and missing pieces
- SaleMethod enum (REGULAR/LOTTERY) and LotteryWindow value object with isOpen(Instant) check
- Plan 4 acceptance tests: SuccessfulLotteryRegistration, LotteryNotSupported, DuplicateLotteryRegistration, ClosedLotteryRegistration

Files / components affected:
- src/main/java/com/ticketing/domain/event/Event.java (suggenst to add saleMethod, lotteryWindow fields + query methods)
- src/main/java/com/ticketing/application/CreateEventRequest.java (added optional saleMethod/lotteryWindow)
- src/test/java/com/ticketing/application/LotteryRegistrationTest.java

Modifications made:
- Created SaleMethod enum and LotteryWindow value object in the domain layer
- Extended Event aggregate with lottery fields, backward-compatible constructors, and isLottery()/isLotteryRegistrationOpen() methods
- Added registerForLottery() to EventService validating: event supports lottery, registration window is open, member not already registered
- Planned 4 acceptance tests matching the V0 spec

Initial gaps in understanding (if any):

Final understanding: LLM helpend plan the necessary changes and helped designing the test.

## Feature / Component: Issue #12 (INF-11 — UI wireframes) - HTML wireframe generation
Purpose of LLM use: Generate plain HTML mid-fidelity B&W wireframes from the layout briefs so we don't have to draw 12 screens by hand in draw.io.
Summary of prompt(s):
1. "Generate HTML wireframes for each screen — black and white, mid-fidelity, no JS, no images, just boxes and labels."
Output received (short description):
- 12 self-contained HTML files (one per screen) using inline CSS — system fonts, outlined buttons, grey placeholder rectangles for images.
- Each file is 1280×800, opens in a browser, can be print-to-PDF or screenshot for the deliverable.
Files / components affected:
- docs/wireframes/01-login-register.html through 12-lottery-registration.html (all new)
- docs/wireframes/README.md (filename table + per-screen layout briefs)
- README.md (Documentation section linking to wireframes folder)
Modifications made:
- HTML wireframes drafted from the layout briefs in the README.
- Print-to-PDF instructions in the README so a human can convert to the PNG/PDF format the spec asks for.
Initial gaps in understanding (if any):
Final understanding (brief explanation in your own words):


## Feature / Component: uc 37
Purpose of LLM use: create examples for tests
Summary of prompt(s): complete test details and fix syntax
Output received (short description): complete calls in tests
Files / components affected: src\test\java\com\ticketing\application\MemberServiceUpdateIdentifyingDetailsTest.java
  
  
## Feature / Component: UC-I.4 (Ticket Issuance Failover & Acceptance Tests)
Purpose of LLM use: Verify UC-I.4 implementation status, implement missing ticket supply gateway failover, refund escalation logic, and add TDD tests for edge cases.
Summary of prompt(s):
1. "check if this was implemented" based on ITicketSupplyGateway and OrderService rules.
2. "add tests for this use case. checkout the format that is already exists... checked all acceptance tests"
Output received (short description):
- Added 4 acceptance tests to `OrderServiceTest` (for secondary failover, all-failing, partial cancellations, and refund rejection).

## Feature / Component: UC-II.3.2 — Open production company
- Purpose of LLM use: Tests help
- Summary of prompt(s): Helped me with consistent mocking and validation, shortening writing time repetetive code (with slight changes for each case).
- Output received (short description): Test code using mockito and integration
- Files / components affected: RevokePersonnelHandlerTest, CompanyServiceTest
- Modifications made: written above.
- Initial gaps in understanding (if any): none
- Final understanding (brief explanation in your own words): helped me understand the sequence of the checking better.

## Feature / Component: UC-II.3.2 — Open production company
- Purpose of LLM use: Skeleton code help
- Summary of prompt(s): Helped me with constructing basic functions without too much logic.
- Output received (short description): skeleton code base
- Files / components affected: company files in the domain layer
- Modifications made: documentation and some corrections to make it suitable and better to understand.
- Initial gaps in understanding (if any): none
- Final understanding (brief explanation in your own words): helped me understand better the fundamentals of the project we just discussed in theory.

## Feature / Component: UC-C.5 — Respond to role offer
- Purpose of LLM use: Understanding the code of my mates to implement something similar on my own. (and tests)
- Summary of prompt(s): understanding the connections between new classes and helping with skeleton. also helped with tests
- Output received (short description): some code and tests
- Files / components affected: handler, event and service function
- Modifications made: documentation and some corrections to make it suitable and better to understand, and making some tests actually pass.
- Initial gaps in understanding (if any): understand the code of the others to make something similar
- Final understanding (brief explanation in your own words): better understanding of message passing in this project.

## Feature / Component: UC-C.6 — Revoke personnel
- Purpose of LLM use: Tests help
- Summary of prompt(s): Helped me with consistent mocking and validation, shortening writing time repetetive code (with slight changes for each case).
- Output received (short description): Test code using mockito and integration
- Files / components affected: RevokePersonnelHandlerTest, CompanyServiceTest
- Modifications made: documentation and some corrections to make it suitable and better to understand.
- Initial gaps in understanding (if any): none
- Final understanding (brief explanation in your own words): helped me understand the sequence of the checking better.

## Feature / Component: Given/When/Then on the whole test tree
- Purpose of LLM use: I wanted every test method to match our `Given_When_Then` style and get it merged without missing files.
- Summary of prompt(s): Find leftovers, rename them, split follow-up work into small PRs, commit/push/PR; later tidy imports; log this in `llm_usage`.
- Output received (short description): List of stragglers; I renamed ~90 methods in 16 files with a tiny Python script (just `pathlib` + string replace on a hand-made old→new map, longest names first so nothing collides). Ran `mvn test`. Also noted follow-ups for later PRs (drop pointless `Serializable` on `ManagerPermissions`, register the relinquish-ownership listener).
- Files / components affected: assorted tests under `src/test` (admin, permissions, org chart, listeners, concurrency, infra, `Tests.java`, etc.).
- Modifications made: renames only, then organized imports on the files I'd touched.
- Initial gaps in understanding (if any): Tests should be GWT.
- Final understanding (brief explanation in your own words): Tests should be GWT format. AI is great for monotonic tasks like renaming test functions that are never called from anywhere else.

## Feature / Component: Move logic to domain services
- Purpose of LLM use: Help me move code around so the application services aren't depending on many repositories.
- Summary of prompt(s): Move the logic that uses multiple repos into domain services. Make sure the application services only calls the logic.
- Output received (short description): Made new domain services like EventDomainService and moved the heavy code there.
- Files / components affected: EventService, OrderService and their new domain services.
- Modifications made: Took out the extra repositories from the application services and put them in the domain services instead.
- Initial gaps in understanding (if any): Just wanted to save some typing time.
- Final understanding (brief explanation in your own words): The domain services should do the real work and hold the repositories. The application layer is just the entry point now.

## Feature / Component: V2-INF-5 — Automated code coverage reporting (JaCoCo) in CI
- Purpose of LLM use: Guided step-by-step implementation of JaCoCo for better understanding.
- Summary of prompt(s): since i'm not familiar and want to use jacoco, insted of using stack overflow i asked for the LLM (so it could explain better).
- Output received (short description): jacoco plugin in pom, instructions on running tests locally to view the report, and explained how to read JaCoCo coverage metrics (C0, C1).
- Files / components affected: pom.xml, .github/workflows/ci.yml
- Modifications made: Added JaCoCo plugin to pom.xml with excludes for UI and Infrastructure layers, fixed surefire argLine overwrite issue, updated CI pipeline to expose the report artifact.
- Initial gaps in understanding (if any): Unfamiliar with JaCoCo configuration, and interpreting the generated index.html coverage report.
- Final understanding (brief explanation in your own words): Understood how to bind JaCoCo to the Maven test phase, limit coverage to specific packages, and interpret colors and columns of code coverage in the generated report.
