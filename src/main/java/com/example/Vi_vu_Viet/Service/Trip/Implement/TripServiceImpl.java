package com.example.Vi_vu_Viet.Service.Trip.Implement;

import com.example.Vi_vu_Viet.Entity.Auth.UserAccount;
import com.example.Vi_vu_Viet.Entity.Trip.BusCompany;
import com.example.Vi_vu_Viet.Entity.Trip.Route;
import com.example.Vi_vu_Viet.Entity.Trip.Trip;
import com.example.Vi_vu_Viet.Repository.Auth.UserRepository;
import com.example.Vi_vu_Viet.Repository.Trip.BusCompanyRepository;
import com.example.Vi_vu_Viet.Repository.Trip.RouteRepository;
import com.example.Vi_vu_Viet.Repository.Trip.TripRepository;
import com.example.Vi_vu_Viet.Service.Trip.TripService;
import com.example.Vi_vu_Viet.Util.ApplicationCode;
import com.example.Vi_vu_Viet.Util.Enums.BusCompanyStatus;
import com.example.Vi_vu_Viet.Util.Enums.RouteStatus;
import com.example.Vi_vu_Viet.Util.Enums.TripSalesStatus;
import com.example.Vi_vu_Viet.Util.Enums.TripStatus;
import com.example.Vi_vu_Viet.dto.Request.Trip.CreateTripRequest;
import com.example.Vi_vu_Viet.dto.Request.Trip.UpdateTripRequest;
import com.example.Vi_vu_Viet.dto.Response.BaseResponse;
import com.example.Vi_vu_Viet.dto.Response.Trip.TripResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    BusCompanyRepository busCompanyRepository;
    UserRepository userRepository;
    RouteRepository routeRepository;
    TripRepository tripRepository;
    TripResponseMapper tripResponseMapper;

    @Override
    @Transactional(readOnly = true)
    public BaseResponse<List<TripResponse>> getCompanyTrips(UUID companyId) {
        getActiveCompany(companyId);
        List<Trip> trips = tripRepository.findAllByCompanyId(companyId);
        List<TripResponse> results = trips.stream().map(tripResponseMapper::toTripResponse).toList();
        return success(results);
    }

    @Override
    @Transactional
    public BaseResponse<TripResponse> createTrip(UUID companyId, CreateTripRequest request, UserAccount actor) {
        BusCompany company = getActiveCompany(companyId);
        Route route = getActiveCompanyRoute(companyId, request.getRouteId());

        LocalDateTime bookingCutoffAt = request.getBookingCutoffAt() == null
                ? request.getDepartureAt().minusMinutes(30)
                : request.getBookingCutoffAt();
        validateSchedule(request.getDepartureAt(), request.getExpectedArrivalAt(), bookingCutoffAt);
        validateTripCodeAvailable(companyId, request.getTripCode());

        Trip trip = new Trip();
        trip.setCompany_id(company);
        trip.setRoute_id(route);
        trip.setTrip_code(normalizeTripCode(request.getTripCode()));
        trip.setDeparture_at(request.getDepartureAt());
        trip.setExpected_arrival_at(request.getExpectedArrivalAt());
        trip.setBooking_cutoff_at(bookingCutoffAt);
        trip.setStatus(TripStatus.DRAFT);
        trip.setSales_status(TripSalesStatus.CLOSED);
        trip.setCreated_by(actor == null
                ? null
                : userRepository.findById(actor.getId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authenticated user was not found"
                        )));

        Trip savedTrip = tripRepository.save(trip);

        return success(tripResponseMapper.toTripResponse(savedTrip));
    }

    @Override
    @Transactional(readOnly = true)
    public BaseResponse<TripResponse> getCompanyTrip(UUID companyId, UUID tripId) {
        getActiveCompany(companyId);
        Trip trip = getCompanyTripEntity(companyId, tripId);
        return success(tripResponseMapper.toTripResponse(trip));
    }

    @Override
    @Transactional
    public BaseResponse<TripResponse> updateTrip(UUID companyId, UUID tripId, UpdateTripRequest request) {
        getActiveCompany(companyId);
        Trip trip = tripRepository.findByIdAndCompanyIdForUpdate(companyId, tripId)
                .orElseThrow(() -> notFound("Trip was not found for this company"));
        if (trip.getStatus() != TripStatus.DRAFT || trip.getSales_status() != TripSalesStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft trips can be updated");
        }
        if (!hasChanges(request)) {
            throw badRequest("At least one trip field must be provided");
        }

        if (request.getTripCode() != null) {
            String tripCode = normalizeTripCode(request.getTripCode());
            if (tripRepository.countByCompanyIdAndTripCodeExcludingTrip(companyId, tripCode, tripId) > 0) {
                throw badRequest("Trip code already exists for this company");
            }
            trip.setTrip_code(tripCode);
        }

        if (request.getDepartureAt() != null) {
            LocalDateTime oldDepartureAt = trip.getDeparture_at();
            LocalDateTime newDepartureAt = request.getDepartureAt();
            Duration shift = Duration.between(oldDepartureAt, newDepartureAt);
            trip.setDeparture_at(newDepartureAt);
            if (request.getExpectedArrivalAt() == null) {
                trip.setExpected_arrival_at(trip.getExpected_arrival_at().plus(shift));
            }
            if (request.getBookingCutoffAt() == null) {
                trip.setBooking_cutoff_at(trip.getBooking_cutoff_at().plus(shift));
            }
        }
        if (request.getExpectedArrivalAt() != null) {
            trip.setExpected_arrival_at(request.getExpectedArrivalAt());
        }
        if (request.getBookingCutoffAt() != null) {
            trip.setBooking_cutoff_at(request.getBookingCutoffAt());
        }

        validateSchedule(trip.getDeparture_at(), trip.getExpected_arrival_at(), trip.getBooking_cutoff_at());

        Trip savedTrip = tripRepository.save(trip);
        return success(tripResponseMapper.toTripResponse(savedTrip));
    }

    @Override
    @Transactional
    public BaseResponse<TripResponse> publishTrip(UUID companyId, UUID tripId) {
        getActiveCompany(companyId);
        Trip trip = tripRepository.findByIdAndCompanyIdForUpdate(companyId, tripId)
                .orElseThrow(() -> notFound("Trip was not found for this company"));

        if (trip.getStatus() == TripStatus.SCHEDULED && trip.getSales_status() == TripSalesStatus.OPEN) {
            return success(tripResponseMapper.toTripResponse(trip));
        }
        if (trip.getStatus() != TripStatus.DRAFT || trip.getSales_status() != TripSalesStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft trips can be published");
        }

        validateSchedule(trip.getDeparture_at(), trip.getExpected_arrival_at(), trip.getBooking_cutoff_at());
        if (trip.getBooking_cutoff_at().isBefore(LocalDateTime.now())) {
            throw badRequest("Booking cutoff must be in the future when publishing a trip");
        }
        if (trip.getRoute_id().getBase_fare_amount() == null || trip.getRoute_id().getBase_fare_amount() <= 0) {
            throw badRequest("The route must have a positive default fare before publishing");
        }

        trip.setStatus(TripStatus.SCHEDULED);
        trip.setSales_status(TripSalesStatus.OPEN);
        Trip savedTrip = tripRepository.save(trip);
        return success(tripResponseMapper.toTripResponse(savedTrip));
    }

    private BusCompany getActiveCompany(UUID companyId) {
        return busCompanyRepository.findByIdAndStatus(companyId, BusCompanyStatus.ACTIVE)
                .orElseThrow(() -> notFound("Bus company was not found or is inactive"));
    }

    private Route getActiveCompanyRoute(UUID companyId, UUID routeId) {
        Route route = routeRepository.findByIdAndCompanyId(companyId, routeId)
                .orElseThrow(() -> notFound("Route was not found for this company"));
        if (route.getStatus() != RouteStatus.ACTIVE) {
            throw badRequest("Route is inactive");
        }
        return route;
    }

    private Trip getCompanyTripEntity(UUID companyId, UUID tripId) {
        return tripRepository.findByIdAndCompanyId(companyId, tripId)
                .orElseThrow(() -> notFound("Trip was not found for this company"));
    }

    private void validateSchedule(
            LocalDateTime departureAt,
            LocalDateTime expectedArrivalAt,
            LocalDateTime bookingCutoffAt
    ) {
        if (departureAt == null || expectedArrivalAt == null || bookingCutoffAt == null) {
            throw badRequest("Departure, expected arrival, and booking cutoff are required");
        }
        if (!expectedArrivalAt.isAfter(departureAt)) {
            throw badRequest("Expected arrival must be later than departure");
        }
        if (!bookingCutoffAt.isBefore(departureAt)) {
            throw badRequest("Booking cutoff must be earlier than departure");
        }
    }

    private void validateTripCodeAvailable(UUID companyId, String tripCode) {
        String normalized = normalizeTripCode(tripCode);
        if (tripRepository.countByCompanyIdAndTripCode(companyId, normalized) > 0) {
            throw badRequest("Trip code already exists for this company");
        }
    }

    private String normalizeTripCode(String tripCode) {
        if (!StringUtils.hasText(tripCode)) {
            throw badRequest("Trip code must not be blank");
        }
        return tripCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasChanges(UpdateTripRequest request) {
        return request.getTripCode() != null
                || request.getDepartureAt() != null
                || request.getExpectedArrivalAt() != null
                || request.getBookingCutoffAt() != null;
    }

    private BaseResponse<TripResponse> success(TripResponse tripResponse) {
        BaseResponse<TripResponse> response = new BaseResponse<>(ApplicationCode.SUCCESS);
        response.setWsResponse(tripResponse);
        return response;
    }

    private BaseResponse<List<TripResponse>> success(List<TripResponse> trips) {
        BaseResponse<List<TripResponse>> response = new BaseResponse<>(ApplicationCode.SUCCESS);
        response.setWsResponse(trips);
        return response;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
