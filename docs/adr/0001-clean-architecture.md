# ADR 0001: Clean Architecture & Modularization

Date: 2025-09-12

## Status
Accepted

## Context
We want a maintainable codebase with clear separation of concerns and scalable build times.

## Decision
- Multi-module Kotlin project
- Layers: core(common, domain, model), data(repo, db, network), feature modules, ui/design-system, app shell
- MVVM with use-cases, Hilt for DI, Room for persistence, Compose for UI

## Consequences
- Faster builds via parallelism and cacheability
- Clear ownership per module
- More boilerplate initially, but better long-term velocity
