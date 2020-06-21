package com.example.reservationservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }


    @Bean
    RouterFunction<ServerResponse> route(ReservationRepository rr){
        return RouterFunctions
                .route()
                .GET("/reservations", serverRequest -> ok().body(rr.findAll(), Reservation.class))
                .build();
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingRequest{
    private String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingResponse{
    private String greeting;
}

@Component
class IntervalMessageProducer{
    Flux<GreetingResponse> produceGreetings(GreetingRequest name){
        return Flux.fromStream(Stream.generate(() -> "Hello" + name.getName() + " @ " + Instant.now()))
                .map(GreetingResponse::new)
                .delayElements(Duration.ofSeconds(1));
    }
}

@RestController
@RequiredArgsConstructor
class ReservationRestController{

    private final ReservationRepository reservationRepository;
    private final IntervalMessageProducer imp;

    @GetMapping("/reservations")
    Publisher<Reservation> getReservations(){
        return this.reservationRepository.findAll();
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/sse/{n}")
    Publisher<GreetingResponse> sse(@PathVariable String n){
        return this.imp.produceGreetings(new GreetingRequest(n));
    }

}

@Component
@RequiredArgsConstructor
@Log4j2
class SampleDataInitializer {

    private final ReservationRepository reservationRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        var saved = Flux.just("Aldo", "Agnese", "Flutra", "Petrit", "Blerta", "Gennaro", "Rita")
                .map(name -> new Reservation(null, name))
                .flatMap(this.reservationRepository::save);

        reservationRepository.deleteAll()
                .thenMany(saved)
                .thenMany(this.reservationRepository.findAll())
//                .subscribeOn(Schedulers.fromExecutor(Executors.newSingleThreadExecutor()))
                .subscribe(log::info);

    }

}

interface ReservationRepository extends ReactiveCrudRepository<Reservation, String> {

}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

    @Id
    private String id;

    private String name;
}
