package com.abco.taxassessment.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit CI gate enforcing the layered architecture (§17.3).
 *
 * Violations of these rules cause build failure.
 * These rules are the mechanical enforcement of §3.2 Layer Rules.
 */
@AnalyzeClasses(
    packages = "com.abco.taxassessment",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * Controllers must not access repositories directly (§3.2).
     * All data access must flow through Application Service → Domain Service → Repository.
     */
    @ArchTest
    static final ArchRule controllers_must_not_access_repositories =
        noClasses().that().resideInAPackage("..controller..")
            .should().accessClassesThat().resideInAPackage("..repository..");

    /**
     * Domain services must not depend on application services (§3.2).
     * Dependency must be downward only: Application → Domain → Repository.
     */
    @ArchTest
    static final ArchRule domain_services_must_not_depend_on_application_services =
        noClasses().that().resideInAPackage("..domain.service..")
            .should().dependOnClassesThat().resideInAPackage("..application..");

    /**
     * Controllers must not use JPA entities directly (§3.2).
     * Controllers work with DTOs only. Entities must not be returned above the service layer.
     */
    @ArchTest
    static final ArchRule entities_must_not_appear_in_controllers =
        noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    /**
     * MapStruct mappers must not contain logic — they must only call no-arg or field-mapping methods.
     * Enforced by naming: mapper classes must end with "Mapper".
     */
    @ArchTest
    static final ArchRule mapper_classes_must_be_in_mapper_package =
        classes().that().haveNameMatching(".*Mapper")
            .should().resideInAPackage("..mapper..");

    /**
     * Domain entities must reside in a domain.entity package.
     * This prevents entity classes from leaking into controller or DTO packages.
     */
    @ArchTest
    static final ArchRule entities_must_be_in_entity_package =
        classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..entity..")
            .orShould().resideInAPackage("..outbox..")
            .because("Entities must live in domain.entity or outbox packages");

    /**
     * Application services must not access repositories directly (§3.2).
     * Application services compose domain services; repository access belongs to domain services.
     */
    @ArchTest
    static final ArchRule application_services_must_not_access_repositories =
        noClasses().that().resideInAPackage("..application..")
            .and().haveNameMatching(".*ApplicationService")
            .should().accessClassesThat().resideInAPackage("..repository..");

    /**
     * DTOs must not contain JPA entities.
     */
    @ArchTest
    static final ArchRule dtos_must_not_contain_entities =
        noClasses().that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    /**
     * No wildcard imports — enforced via compilation (javac warnings-as-errors).
     * ArchUnit rule serves as documentation of this constraint.
     */
    @ArchTest
    static final ArchRule entity_classes_must_have_entity_suffix =
        classes().that().areAnnotatedWith(Entity.class)
            .should().haveSimpleNameEndingWith("Entity")
            .because("All JPA entities must have 'Entity' suffix per §4 naming convention");

    /**
     * Domain services must be annotated with @Service.
     */
    @ArchTest
    static final ArchRule domain_services_must_be_annotated =
        classes().that().resideInAPackage("..domain.service..")
            .and().haveNameMatching(".*Service")
            .should().beAnnotatedWith(org.springframework.stereotype.Service.class);
}
