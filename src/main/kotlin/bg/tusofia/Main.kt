package bg.tusofia

import java.math.BigInteger
import java.security.SecureRandom

object ShamirSecretSharing {

    private val random = SecureRandom()

    /**
     * Generates shares for the secret using Shamir's Secret Sharing.
     * @param secret The secret to be shared.
     * @param totalShares Total number of shares to generate.
     * @param threshold Minimum number of shares needed to reconstruct the secret.
     * @param prime Prime number larger than the secret.
     * @return A list of shares as pairs (x, y).
     */
    fun splitSecret(
        secret: BigInteger,
        totalShares: Int,
        threshold: Int,
        prime: BigInteger
    ): List<Pair<BigInteger, BigInteger>> {
        require(threshold <= totalShares) { "Threshold cannot exceed total shares." }
        require(secret < prime) { "Secret must be smaller than the prime number." }

        val coefficients = mutableListOf<BigInteger>()

        coefficients.add(secret) // Constant term (the secret)

        // Generate random coefficients for the polynomial
        for (i in 1 until threshold) {
            coefficients.add(BigInteger(prime.bitLength(), random).mod(prime))
        }

        printPolynomial(coefficients)

        val shares = mutableListOf<Pair<BigInteger, BigInteger>>()
        for (i in 1..totalShares) {
            val x = BigInteger.valueOf(i.toLong())
            val y = evaluatePolynomial(coefficients, x, prime)
            shares.add(Pair(x, y))
        }
        return shares
    }

    fun printPolynomial(coefficients: List<BigInteger>) {
        val polynomial = StringBuilder()
        for (i in coefficients.indices) {
            val coefficient = coefficients[i]
            if (coefficient != BigInteger.ZERO) {
                if (polynomial.isNotEmpty() && coefficient > BigInteger.ZERO) {
                    polynomial.append(" + ")
                } else if (coefficient < BigInteger.ZERO) {
                    polynomial.append(" - ")
                }
                val absCoefficient = coefficient.abs()
                if (i == 0 || absCoefficient != BigInteger.ONE) {
                    polynomial.append(absCoefficient)
                }
                if (i > 0) {
                    polynomial.append("x")
                    if (i > 1) {
                        polynomial.append("^$i")
                    }
                }
            }
        }
        println("Polynomial: $polynomial")
    }

    /**
     * Reconstructs the secret from the given shares using Lagrange interpolation.
     * @param shares List of shares as pairs (x, y).
     * @param prime The prime number used during the secret splitting.
     * @return The reconstructed secret.
     */
    fun reconstructSecret(
        shares: List<Pair<BigInteger, BigInteger>>,
        prime: BigInteger
    ): BigInteger {
        var secret = BigInteger.ZERO

        for (i in shares.indices) {
            val (xi, yi) = shares[i]

            var numerator = BigInteger.ONE
            var denominator = BigInteger.ONE

            for (j in shares.indices) {
                if (i != j) {
                    val xj = shares[j].first
                    numerator = numerator.multiply(xj.negate()).mod(prime)
                    denominator = denominator.multiply(xi.subtract(xj)).mod(prime)
                }
            }

            val lagrangeTerm = yi.multiply(numerator).multiply(denominator.modInverse(prime)).mod(prime)
            secret = secret.add(lagrangeTerm).mod(prime)
        }

        return secret
    }

    /**
     * Evaluates a polynomial at a given point x.
     * @param coefficients Coefficients of the polynomial.
     * @param x The point at which to evaluate.
     * @param prime The prime number used to reduce the result.
     * @return The value of the polynomial at x -> y.
     */
    private fun evaluatePolynomial(
        coefficients: List<BigInteger>,
        x: BigInteger,
        prime: BigInteger
    ): BigInteger {
        var result = BigInteger.ZERO
        var power = BigInteger.ONE

        for (coefficient in coefficients) {
            result = result.add(coefficient.multiply(power)).mod(prime)
            power = power.multiply(x).mod(prime)
        }

        return result
    }
}

fun main() {
    val prime = BigInteger.probablePrime(128, SecureRandom())
    val totalShares = 5
    val threshold = 3

    val partySecrets = listOf(
        BigInteger("12345"),
        BigInteger("67890"),
        BigInteger("11111"),
        BigInteger("22222"),
        BigInteger("33333"),
    )

    println("Party Secrets: $partySecrets")
    println("Prime: $prime")
    println()

    // Each party splits their secret into shares
    val allShares = partySecrets.mapIndexed { index, secret ->
        println("Shares for Party ${index + 1}:")
        val shares = ShamirSecretSharing.splitSecret(secret, totalShares, threshold, prime)
        shares.forEach { (x, y) -> println("x: $x, y: $y") }
        shares
    }

    // Each party gets one share from others
    val exchangedShares = MutableList(partySecrets.size) { mutableListOf<Pair<BigInteger, BigInteger>>() }
    for (shares in allShares) {
        for ((i, share) in shares.withIndex()) {
            exchangedShares[i % partySecrets.size].add(share)
        }
    }

    println()
    println("Exchanged Shares (after distribution):")
    exchangedShares.forEachIndexed { partyIndex, shares ->
        println("Party ${partyIndex + 1} now holds:")
        shares.forEach { println("x: ${it.first}, y: ${it.second}") }
    }

    // Local sums of exchanged shares
    val localSums = exchangedShares.map { partyShares ->
        val x = partyShares.first().first
        val ySum = partyShares.map { it.second }.reduce { acc, y -> acc.add(y).mod(prime) }
        Pair(x, ySum)
    }

    println()
    println("Local Sums:")
    localSums.forEach { println("x: ${it.first}, y: ${it.second}") }

    val subset = localSums.shuffled().take(threshold) // 3
//    val subset = localSums.shuffled().take(2)
//    val subset = localSums.shuffled().take(4)
//    val subset = localSums.shuffled().take(5)

    println()
    val sumOfSecrets = ShamirSecretSharing.reconstructSecret(subset, prime)
    println("Sum of Secrets: $sumOfSecrets")

    val expectedSum = partySecrets.reduce { acc, secret -> acc.add(secret).mod(prime) }
    println("Expected Sum: $expectedSum")

    if (sumOfSecrets == expectedSum) {
        println("The sum of the secrets was successfully reconstructed.")
    } else {
        println("The reconstruction of the sum failed!")
    }
}
