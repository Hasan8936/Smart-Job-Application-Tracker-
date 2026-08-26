package com.smartjobtracker.model;

import jakarta.persistence.*;

@Entity @Table(name="application_field_mappings", uniqueConstraints=@UniqueConstraint(columnNames={"preparation_id","external_field"}))
public class ApplicationFieldMapping {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="preparation_id", nullable=false) private Long preparationId;
    @Column(name="external_field", nullable=false) private String externalField;
    @Enumerated(EnumType.STRING) @Column(name="field_type", nullable=false) private ApplicationFieldType fieldType;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getPreparationId(){return preparationId;} public void setPreparationId(Long v){preparationId=v;}
    public String getExternalField(){return externalField;} public void setExternalField(String v){externalField=v;} public ApplicationFieldType getFieldType(){return fieldType;} public void setFieldType(ApplicationFieldType v){fieldType=v;}
}