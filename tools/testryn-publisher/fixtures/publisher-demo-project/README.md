# publisher-demo-project

A small, real Maven/JUnit 5 project that exists **only** to produce genuine Surefire
XML for verifying `testryn-publisher publish-junit`. It is not part of the Testryn
product, has no parent-module relationship to the rest of the repository, and is not
run by any CI pipeline.

## Purpose

The JUnit XML Import block's verification requirement is explicit: a hand-written
XML file alone does not count as proof the reader works against real Surefire output.
This project supplies four tests whose `classname#name` automationReference exactly
matches four Testryn Test Cases created for the verification pass:

| Test method | automationReference | Expected outcome |
|---|---|---|
| `passedTest` | `com.example.PublisherDemoTest#passedTest` | PASSED |
| `failedTest` | `com.example.PublisherDemoTest#failedTest` | FAILED |
| `skippedTest` | `com.example.PublisherDemoTest#skippedTest` | SKIPPED (`@Disabled`) |
| `anotherPassedTest` | `com.example.PublisherDemoTest#anotherPassedTest` | PASSED |

## Regenerating the Surefire XML

```bash
cd tools/testryn-publisher/fixtures/publisher-demo-project
mvn test
```

`testFailureIgnore` is set so the build completes (and all four `TEST-*.xml` files
are written to `target/surefire-reports/`) despite `failedTest` failing on purpose.
