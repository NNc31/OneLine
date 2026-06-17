package com.nefodov.oneline;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

@AnalyzeClasses(packages = "com.nefodov.oneline", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_must_not_touch_repositories_directly = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .because("controllers must go through services, not data repositories");

    @ArchTest
    static final ArchRule dtos_must_not_depend_on_jpa = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
            .because("DTOs are transport types and must not leak the persistence model");

    @ArchTest
    static final ArchRule dtos_must_not_be_spring_beans = noClasses()
            .that().resideInAPackage("..dto..")
            .should().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Component.class)
            .orShould().beAnnotatedWith(Controller.class)
            .orShould().beAnnotatedWith(RestController.class)
            .because("DTOs are plain data carriers, not Spring components");

    @ArchTest
    static final ArchRule repositories_live_in_feature_packages = classes()
            .that().areAssignableTo(Repository.class).and().areInterfaces()
            .should().resideInAnyPackage("..chat..", "..message..", "..attachment..")
            .because("data repositories belong to their feature package");

    @ArchTest
    static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}
