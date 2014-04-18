# HyperLogLog

# Glossary

* index bits: part of the hash used to represent the bucket index
* value bits: part of the hash used to compute the number of leading 0s
* `p`: Number of bits needed to represent bucket indexes
* `v`: Number of leading 0s 
* short hash: topmost 16 bits of the original hash
* long hash: topmost 64 bits of the original hash

## Hash function

* Murmur3-128
* Bucket index computed from the topmost `p` bits of the 128-bit hash

* To hash single byte, short, int, long, convert to little-endian byte representation
* To hash float or double, convert to 4 or 8-byte IEEE-754 representation

![Hash](hash.png)

## Format

![General](general.png)

### Notes

* Unless otherwise noted, all values are little-endian.
* The following format specification is suitable for `p <= 13` (i.e., 8192 buckets).
`13 < p <= 16` result in too many overflows (see below), which will result in degraded
performance and accuracy. `p > 16` cannot be represented at all. Support for `p > 13` to
be specified at a later time.

### Sparse layout

![Sparse](sparse.png)

* short hashes are sorted in increasing order, no duplicates
* overflow entries are sorted in increasing order by bucket index, no duplicate bucket indexes

#### Overflow entries

Overflow entries are needed when the number of leading zeros (`v`) in the original hash cannot be determined by inspecting the short hash. Consider the following two scenarios:

* Case 1: overflow entry not needed (`v <= 16 - p`)

![Sparse overflow, case 1](sparse-overflow-case-1.png)

* Case 2: overflow entry needed (`v > 16 - p`)

![Sparse overflow, case 2](sparse-overflow-case-2.png)

##### Overflow entry layout

![Sparse overflow entry](sparse-overflow-entry.png)


### Dense layout

![Dense](dense.png)

Bucket value are stored as deltas from a baseline value, which is computed as:

`baseline = min(buckets)`

The buckets values are encoded as a sequence of 4-bit values:

![Dense buckets](dense-buckets.png)

Based on the statistical properties of the HLL algorithm, 4-bits should be sufficient to encode
the majority of the values in a given HLL structure. In the unlikely case that a bucket overflows,
the remainder is stored in an overflow entry. Only the highest overflow is kept around.

