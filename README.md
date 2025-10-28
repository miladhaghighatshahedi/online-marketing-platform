# Online Marketing Platform
# Spring Boot # Java # Spring Modulith # Spring Data JDBC # MapStruct


Online Marketing Platform is a Spring Modulith-based web application developed in Java.
The project consists of multiple modules designed to reduce dependencies between different parts of the application.
The data access layer is built on Spring Data JDBC, and the database used for this project is Postgres.

The coding style I have followed is the package-private modular design, which is intended to keep the internal implementations of each mini-module (here each distinct java file) hidden from the outside.
Another design pattern applied in this project is CQRS (Command Query Responsibility Segregation), which helps separate read and write operations for better scalability and maintainability.

A few of the modules included in the project are:

A) Advertisement

B) Catalog

C) Common

D) Credential

E) Location

F) Profile

As of the time of writing this README file, some of these modules are only partially developed or not yet implemented, but they are planned to be completed soon.

MapStruct is used as a code generation tool to simplify DTO mapping in this project.
It is also worth mentioning that a few necessary public methods have been added to certain service implementation classes to expose specific functionality to other modules, 
for instance CategoryService implements CategoryApi interface which contains a single method existsById(UUID category); to expose this method to other modules, this method
was the only method in CategoryService which was set to public till I added caching functionality to this service which needed some methods to be set to public.



