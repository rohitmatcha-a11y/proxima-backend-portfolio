package com.jobportal.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "continent_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContinentMapping {

    @Id
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "continent", nullable = true, length = 50)
    private String continent;
}
