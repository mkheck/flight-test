package com.thehecklers.flighttest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FlightTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightTestApplication.class, args);
    }

    @Bean
    WebClient client() {
        return WebClient.create("https://opensky-network.org/api");
    }
}

@RestController
class FTController {
    private final WebClient client;
    private final Boundary boundary;
    private final String boundaryQuery;

    public FTController(WebClient client, Boundary boundary) {
        this.client = client;
        this.boundary = boundary;

        this.boundaryQuery = "?lamin=" + boundary.getLatMin() +
                "&lomin="+ boundary.getLonMin() +
                "&lamax=" + boundary.getLatMax() +
                "&lomax=" + boundary.getLonMax();
    }

    @GetMapping
    Mono<String> testing123() {
        return client.get()
                .uri("/states/all" + boundaryQuery)
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/positions")
    Flux<Position> getStates(@RequestParam(required = false) String oc,
                             @RequestParam(required = false) String tracklo,
                             @RequestParam(required = false) String trackhi) {
        return client.get()
                .uri("/states/all" + boundaryQuery)
                .retrieve()
                .bodyToFlux(PositionReport.class)
                .flatMap(pr -> Flux.fromIterable(pr.getPositions()))
                .filter(pos -> null == oc || pos.origin_country().equalsIgnoreCase(oc))
                .filter(pos -> (null == tracklo || null == trackhi) ||
                        (pos.true_track() > Float.parseFloat(tracklo) && (pos.true_track() < Float.parseFloat(trackhi))));
    }

    @GetMapping("/countries")
    Flux<String> getCountriesOfCurrentFlights() {
        return client.get()
                .uri("/states/all" + boundaryQuery)
                .retrieve()
                .bodyToFlux(PositionReport.class)
                .flatMap(pr -> Flux.fromIterable(pr.getPositions()))
                .map(pos -> pos.origin_country() + "\n")
                .distinct()
                .sort();
    }
}

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class PositionReport {
    private Integer time;
    private String[][] states;
    private List<Position> positions = new ArrayList<>();

    public void setStates(String[][] states) {
        this.states = states;
        positions.clear();
        for (String[] state : states) {
            Position p = createPositionFromArray(state);
            positions.add(p);
        }
    }

    private Position createPositionFromArray(String[] state) {
        return new Position(state[0],
                state[1].trim(),
                state[2].trim(),
                null == state[5] ? -1F : Float.parseFloat(state[5]),
                null == state[6] ? -1F : Float.parseFloat(state[6]),
                null == state[7] ? -1F : Float.parseFloat(state[7]),
                null == state[9] ? -1F : Float.parseFloat(state[9]),
                null == state[10] ? -1F : Float.parseFloat(state[10]),
                null == state[11] ? -1F : Float.parseFloat(state[11]),
                null == state[13] ? -1F : Float.parseFloat(state[13]),
                null == state[14] ? "" : state[14].trim(),
                null != state[15] && Boolean.getBoolean(state[15]),
                null == state[16] ? -1 : Integer.parseInt(state[16]));
    }

    @Override
    public String toString() {
        return "PositionReport{" +
                "time=" + time +
                ", positions=" + positions +
                '}';
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Position(String icao24,
			 String callsign,
			 String origin_country,
			 float longitude,
			 float latitude,
			 float baro_altitude,
			 float velocity,
			 float true_track,
			 float vertical_rate,
			 float geo_altitude,
			 String squawk,
			 boolean spi,
			 int position_source) {}

@Value
@ConfigurationProperties(prefix = "boundary")
class Boundary {
    float latMin, lonMin, latMax, lonMax;

    @ConstructorBinding
    public Boundary(@Name("latitude.minimum") float latMin,
                    @Name("longitude.minimum") float lonMin,
                    @Name("latitude.maximum") float latMax,
                    @Name("longitude.maximum") float lonMax) {
        this.latMin = latMin;
        this.lonMin = lonMin;
        this.latMax = latMax;
        this.lonMax = lonMax;
    }
}