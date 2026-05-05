package com.ticketing.infrastructure;

import java.util.Hashtable;
import java.util.Optional;
import java.util.UUID;

import com.ticketing.domain.member.IRoleAppointmentOfferRepository;
import com.ticketing.domain.member.RoleAppointmentOffer;

public class InMemoryRoleAppointmentOfferRepository implements IRoleAppointmentOfferRepository {
    private final Hashtable<UUID, RoleAppointmentOffer> offers = new Hashtable<>();

    @Override
    public void save(RoleAppointmentOffer offer) {
        if (offer == null) return;
        offers.put(offer.getOfferId(), offer);
    }

    @Override
    public Optional<RoleAppointmentOffer> findById(UUID offerId) {
        return Optional.ofNullable(offers.get(offerId));
    }
}
