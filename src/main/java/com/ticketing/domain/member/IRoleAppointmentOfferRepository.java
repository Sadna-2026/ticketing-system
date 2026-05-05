package com.ticketing.domain.member;

import java.util.Optional;
import java.util.UUID;

public interface IRoleAppointmentOfferRepository {
    void save(RoleAppointmentOffer offer);
    Optional<RoleAppointmentOffer> findById(UUID offerId);
}
