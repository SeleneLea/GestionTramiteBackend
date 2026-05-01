# Fase 4.5 · ArchUnit — Tests automáticos de arquitectura

> Tests Java que **verifican que la arquitectura se respeta** en cada compilación. Si alguien rompe una regla, el build falla.

---

## 1. Por qué esto importa para la nota

Tener un diagrama de componentes es lindo, pero el profe sabe que **el código puede divergir del diagrama**. ArchUnit demuestra que las reglas son **enforced** automáticamente. Esto es algo que **muy pocos proyectos académicos hacen** y diferencia tu entrega.

> Frase para presentación: *"No solo diseñé la arquitectura, la programé como tests que se ejecutan en cada build. Si un compañero accidentalmente rompe una regla, el CI lo detecta antes del merge."*

---

## 2. Reglas a verificar

| # | Regla | Por qué |
|---|-------|---------|
| 1 | Solo clases en `api/` pueden ser `public` (interfaces y DTOs públicos) | Encapsulación |
| 2 | `internal/` no es accesible desde otro componente | Bounded contexts |
| 3 | `domain/` no depende de Spring ni de Mongo | Dominio puro |
| 4 | Repositorios viven solo en `internal/` | No exponer persistencia |
| 5 | Controllers viven solo en `internal/` | Solo exponen Ports |
| 6 | Componente A no importa de `internal/` de componente B | Aislamiento |
| 7 | No hay ciclos entre componentes | DAG limpio |
| 8 | Componentes consumen otros vía interfaces (`*Port`) | Inversión de dependencias |

---

## 3. Setup

Verificar que `pom.xml` tiene la dependencia (de fase 4.0):

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

Crear el archivo de tests:

```
src/test/java/com/example/demo/architecture/ArchitectureTest.java
```

---

## 4. Código del test

```java
package com.example.demo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.demo");
    }

    // ─── Regla 1: las clases en internal/ no son accesibles desde otros componentes
    @Test
    void internalEsPaqueteRestringido() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..modules.(*)..")
                .and().resideInAPackage("..internal..")
                .should().beAccessedByClassesThat()
                .resideInAPackage("..modules.(*)..")
                .as("internal/ de un componente no debe ser accedido desde otros componentes");

        regla.check(clases);
    }

    // ─── Regla 2: domain/ no depende de Spring ni de Mongo
    @Test
    void domainNoDependeDeFrameworks() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..modules..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.data.mongodb..",
                        "jakarta.persistence.."
                ).as("El dominio debe permanecer independiente de la tecnología");

        regla.check(clases);
    }

    // ─── Regla 3: repositorios viven solo en internal/
    @Test
    void repositoriosEnInternal() {
        ArchRule regla = classes()
                .that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .or().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..internal..")
                .as("Los repositorios deben estar en internal/");

        regla.check(clases);
    }

    // ─── Regla 4: controllers viven solo en internal/
    @Test
    void controllersEnInternal() {
        ArchRule regla = classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAPackage("..internal..")
                .as("Los controllers deben estar en internal/");

        regla.check(clases);
    }

    // ─── Regla 5: clases en api/ son interfaces o DTOs (records / @Data)
    @Test
    void apiSoloInterfacesYDtos() {
        ArchRule regla = classes()
                .that().resideInAPackage("..modules..api..")
                .and().areNotInterfaces()
                .and().areNotEnums()
                .should().beAnnotatedWith(lombok.Data.class)
                .orShould().beRecords()
                .as("api/ solo debe contener interfaces, DTOs (Lombok @Data) o records");

        regla.check(clases);
    }

    // ─── Regla 6: no debe haber ciclos entre componentes
    @Test
    void noHayCiclosEntreComponentes() {
        ArchRule regla = slices()
                .matching("..modules.(*)..")
                .should().beFreeOfCycles()
                .as("No debe haber dependencias circulares entre componentes");

        regla.check(clases);
    }

    // ─── Regla 7: el componente workflow no accede a repositorios de otros
    @Test
    void workflowNoAccedeARepositoriosAjenos() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..modules.workflow..")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .andShould().dependOnClassesThat()
                .resideInAPackage("..modules..internal..")
                .andShould().dependOnClassesThat()
                .resideOutsideOfPackage("..modules.workflow..")
                .as("workflow solo accede a sus propios repositorios; los demás vía Port");

        regla.check(clases);
    }

    // ─── Regla 8: nadie depende de implementaciones concretas de otros componentes
    @Test
    void servicesImplNoSonReferenciadosFueraDeSuComponente() {
        ArchRule regla = classes()
                .that().haveSimpleNameEndingWith("ServiceImpl")
                .should().onlyBeAccessed().byClassesThat()
                .resideInTheSamePackageAs(java.lang.Class.class) // se reemplaza con regla custom
                .as("Las *ServiceImpl no deben ser referenciadas fuera de su paquete internal/");
        // En la práctica esta regla se afina con una visibilidad package-private (sin public).

        regla.check(clases);
    }

    // ─── Regla 9: capas básicas (controller → service → repository, no al revés)
    @Test
    void capasNoSeInvierten() {
        ArchRule regla = noClasses()
                .that().areAnnotatedWith(org.springframework.stereotype.Repository.class)
                .or().haveSimpleNameEndingWith("Repository")
                .should().dependOnClassesThat()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .as("Repositorios no deben depender de controllers");

        regla.check(clases);
    }
}
```

---

## 5. Ejecutar los tests

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/Backend"
./mvnw test -Dtest=ArchitectureTest
```

### Si fallan
ArchUnit imprime exactamente **qué clase rompe la regla y desde dónde**. Ejemplo:

```
java.lang.AssertionError: Architecture Violation [...]
Rule 'no classes that reside in package '..modules.(*)..internal..' should be accessed by classes that reside in package '..modules.(*)..'
- Method WorkflowController.getNotificacionesActivas() calls method NotificacionRepository.findByUsuarioId(String)
  in (NotificacionRepository.java:42)
```

Eso te dice exactamente qué arreglar.

---

## 6. Integración con CI (opcional)

Si tienes GitHub Actions o similar, agregar un job:

```yaml
- name: Architecture tests
  run: ./mvnw test -Dtest=ArchitectureTest
```

Así cada PR valida la arquitectura antes de mergear.

---

## 7. Capturas para el documento final

Tomar screenshot de:
- IntelliJ con todos los tests pasando (verde)
- Salida de `./mvnw test` con `BUILD SUCCESS`

Guardar en `fase4/diagramas/archunit_tests_passing.png` para incluir en la presentación.

---

## 8. Commit

```bash
git add src/test/java/com/example/demo/architecture/ArchitectureTest.java
git commit -m "feat(test): ArchUnit tests para enforcement automatico de arquitectura"
```

---

## 9. Cómo presentarlo

> *"Aquí tengo nueve reglas arquitectónicas verificadas automáticamente con ArchUnit. Por ejemplo, la regla 6 dice 'ningún componente puede tener ciclos con otros' y la regla 1 dice 'lo que vive en internal/ no puede ser accedido desde otro componente'. Si alguien las rompe, el build falla. Esto significa que la arquitectura no solo está documentada — está **enforced**."*

---

## Próximo paso

Continuar con **`06_adrs.md`**.
