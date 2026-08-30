# Seamless API architecture

This document expands on the compatibility contract in [README.md](README.md).

## Responsibilities

Seamless API defines registrations and query contracts. The consuming gameplay
mod remains authoritative for persistence, ticking, networking, permissions,
and effects. This keeps the API small enough to load on dedicated servers and
prevents integrations from reaching into loader-specific internals.

The 2.x surface is grouped into four areas:

- satiation registrations, buff snapshots, queries, modifiers, and lifecycle
  callbacks;
- deconstruction contexts, registrations, and output modifiers;
- meteor-shower registrations and lifecycle callbacks;
- immutable vectors, trail math, tumble animation, and thrown-item visual
  profiles.

## Bootstrap boundary

Common initialization receives a concrete platform adapter from the Fabric,
Forge, or NeoForge entrypoint. There is no reflection or `ServiceLoader`
discovery. Public code stays under the preserved `com.derko.seamlessapi`
namespace; internal bootstrapping lives under `io.github.derkottersberg`.

## Registration lifecycle

Integrations register descriptors during normal mod initialization. Gameplay
mods may then freeze and consume the resulting immutable views. Registrations
made after a contract has been frozen fail explicitly instead of being ignored.
Runtime queries and callbacks operate only on state supplied by the authoritative
gameplay mod.

## Release relationship

The API is released before dependent Seamless mods. Development and suite CI
build Meteors and Workbench against the exact API source commit recorded in the
suite lock manifest. Release jars declare Seamless API 2.x as an external
dependency and never embed it.
