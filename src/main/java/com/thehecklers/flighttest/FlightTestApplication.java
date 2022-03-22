package com.thehecklers.flighttest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@AllArgsConstructor
class FTController {
    private final WebClient client;
    private final Boundary boundary;

    @GetMapping
    Mono<String> testing123() {
        log.info("Boundary: " + boundary.toString());

        return client.get()
                //.uri("/states/all?lamin=43.5423&lomin=20.1857&lamax=48.0706&lomax=29.4944") (Romania, approximately)
                .uri("/states/all?lamin=" + boundary.getLatMin() +
                        "&lomin="+ boundary.getLonMin() +
                        "&lamax=" + boundary.getLatMax() +
                        "&lomax=" + boundary.getLonMax())
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/positions")
    Flux<Position> getStates(@RequestParam(required = false) String oc,
                             @RequestParam(required = false) String tracklo,
                             @RequestParam(required = false) String trackhi) {
        // MH: Add logic to handle arc from < 360 to > 0, e.g. 271 - 89 for all northerly tracks from WNW to ENE
        return client.get()
                .uri("/states/all?lamin=" + boundary.getLatMin() +
                        "&lomin="+ boundary.getLonMin() +
                        "&lamax=" + boundary.getLatMax() +
                        "&lomax=" + boundary.getLonMax())
                .retrieve()
                .bodyToFlux(PositionReport.class)
                .flatMap(pr -> Flux.fromIterable(pr.getPositions()))
                .filter(pos -> null == oc || pos.getOrigin_country().equalsIgnoreCase(oc))
                .filter(pos -> (null == tracklo || null == trackhi) ||
                        (pos.getTrue_track() > Float.parseFloat(tracklo) && (pos.getTrue_track() < Float.parseFloat(trackhi))));
    }

    @GetMapping("/countries")
    Flux<String> getCountriesOfCurrentFlights() {
        return client.get()
                .uri("/states/all?lamin=" + boundary.getLatMin() +
                        "&lomin="+ boundary.getLonMin() +
                        "&lamax=" + boundary.getLatMax() +
                        "&lomax=" + boundary.getLonMax())
                .retrieve()
                .bodyToFlux(PositionReport.class)
                .flatMap(pr -> Flux.fromIterable(pr.getPositions()))
                .map(pos -> pos.getOrigin_country() + "\n")
                .distinct()
                .sort();
    }
}

@Slf4j
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class Position {
    private String icao24,
            callsign,
            origin_country;
    private float longitude,
            latitude,
            baro_altitude,
            velocity,
            true_track,
            vertical_rate,
            geo_altitude;
    private String squawk;
    private boolean spi;
    private int position_source;
}

/*
@JsonIgnoreProperties(ignoreUnknown = true)
record State(String icao24,
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
*/

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