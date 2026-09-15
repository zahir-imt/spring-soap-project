# 01 · Analysis and requirements

## Problem

A small warehouse needs one reliable record of products and available stock. Browser users and integrated systems must follow the same rules: an order must not reserve unavailable units, partial failures must not change stock, and cancellation must not restore units twice.

## Users and goals

- Warehouse operator: add products, receive stock and identify low-stock items.
- Order operator: place multi-line orders and cancel mistakes.
- Integration developer: read and change inventory through a documented SOAP contract.
- Reviewer: reproduce business behaviour and inspect test evidence.

These are functional personas, not authenticated roles. Version 1 has no login system.

## Requirements and acceptance criteria

| ID | Requirement | Acceptance evidence |
|---|---|---|
| R1 | Unique catalogue entries | Creating `A` and then `a` rejects the second SKU |
| R2 | Safe inputs | Negative stock, non-positive order quantities and prices with more than two decimal places are rejected |
| R3 | Restocking | Adding five units increases stock by five and records a movement |
| R4 | Atomic multi-line orders | If one line lacks stock, all earlier updates in that order roll back |
| R5 | Correct totals | Three units at 12.35 total exactly 37.05 |
| R6 | Concurrency | Two orders of four units against five available result in one success and one business rejection |
| R7 | Repeat-safe cancellation | Cancelling an order twice restores its stock once |
| R8 | Persistence | A created product survives a process restart |
| R9 | SOAP contract | Build generates Java classes; WSDL exposes seven operations |
| R10 | SOAP validation | Invalid XML messages return SOAP faults; missing products return `NOT_FOUND` |
| R11 | Readable tests | Cucumber runs real HTTP SOAP scenarios and emits an HTML report |
| R12 | Usable demonstration | Dashboard supports product creation, ordering, restocking and cancellation |

## Input rules

- SKU: 1–32 ASCII letters, numbers, hyphens or underscores; first character alphanumeric; normalised to uppercase.
- Name and customer: 1–100 nonblank characters after trimming; category: 1–60.
- Price: 0–1,000,000 CAD with at most two meaningful decimal places.
- Stock and reorder threshold: whole numbers from 0–1,000,000.
- Restock/order quantity: whole numbers from 1–1,000,000.
- Order: 1–100 distinct SKU lines. Duplicate lines must be combined by the caller.
- Cancellation may be rejected if restoring units would exceed the stock limit; all changes then roll back.

## Version 1 boundaries

One warehouse, one currency, no taxes, no payment collection, no shipping, no supplier purchasing and no user accounts. Products cannot be edited or deleted in this release. All amounts describe demonstration transactions. The interface loads the catalogue and order list in full; it is intended for a small demonstration dataset.

## Success criteria

A reviewer can start the application, create and cancel an order, inspect the resulting stock changes, call its SOAP interface, and reproduce a passing automated test run. Performance benchmarks, deployment reliability and multi-database compatibility are not claimed without separate evidence.
