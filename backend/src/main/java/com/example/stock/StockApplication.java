// Starts the inventory API and its database-backed health checks.
package com.example.stock;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StockApplication {
    /** Launches the Spring application. */
    public static void main(String[] args) {
        SpringApplication.run(StockApplication.class, args);
    }

    /** Keeps JSON integers and catalog text type-safe while letting decimal values arrive as strings. */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer inventoryJsonTypes() {
        return builder -> builder.postConfigurer(mapper -> {
            mapper.coercionConfigFor(LogicalType.Integer)
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
            mapper.coercionConfigFor(LogicalType.Textual)
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
