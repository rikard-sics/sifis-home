/*******************************************************************************
 * Copyright (c) 2019 Lari Hotari and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Lari Hotari - initial API and implementation
 *******************************************************************************/
package org.eclipse.californium.core.network.stack;

import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.config.Configuration;

/**
 * Contains the configuration values for {@link ReliabilityLayer} and
 * {@link CongestionControlLayer}.
 */
public class ReliabilityLayerParameters {

	private final int ackTimeout;
	private final int maxAckTimeout;
	private final float ackRandomFactor;
	private final float ackTimeoutScale;
	private final int maxRetransmit;
	private final int nstart;

	ReliabilityLayerParameters(int ackTimeout, int maxAckTimeout, float ackRandomFactor, float ackTimeoutScale,
			int maxRetransmit, int nstart) {
		this.ackTimeout = ackTimeout;
		this.maxAckTimeout = maxAckTimeout;
		this.ackRandomFactor = ackRandomFactor;
		this.ackTimeoutScale = ackTimeoutScale;
		this.maxRetransmit = maxRetransmit;
		this.nstart = nstart;
	}

	/**
	 * Initial ACK timeout.
	 * 
	 * @return initial ACK timeout in milliseconds.
	 */
	public int getAckTimeout() {
		return ackTimeout;
	}

	/**
	 * Maximum ACK timeout.
	 * 
	 * @return maximum ACK timeout in milliseconds.
	 * @since 3.0
	 */
	public int getMaxAckTimeout() {
		return maxAckTimeout;
	}

	/**
	 * Random factor for initial ACK retransmission timeout.
	 * 
	 * The initial timeout will be scaled by a random value between 1.0F - and
	 * this upper limit.
	 * 
	 * @return random factor, between 1.0F - upper limit.
	 */
	public float getAckRandomFactor() {
		return ackRandomFactor;
	}

	/**
	 * Scale for ACK retransmission timeout.
	 * 
	 * The timeout will be scaled by this value on retransmission
	 * 
	 * @return factor to scale.
	 */
	public float getAckTimeoutScale() {
		return ackTimeoutScale;
	}

	/**
	 * Maximum number of retransmissions.
	 * 
	 * @return maximum number of retransmissions.
	 */
	public int getMaxRetransmit() {
		return maxRetransmit;
	}

	/**
	 * NSTART, number of concurrent request/transmissions.
	 * 
	 * Note: Only experimentally supported by {@link CongestionControlLayer},
	 * which has known threading issues.
	 * 
	 * @return NSTART
	 */
	public int getNstart() {
		return nstart;
	}

	/**
	 * Create builder for {@link ReliabilityLayerParameters}.
	 * 
	 * @return builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link ReliabilityLayerParameters}.
	 */
	public static final class Builder {

		/**
		 * Initial ACK timeout.
		 */
		private int ackTimeout;
		/**
		 * Maximum ACK timeout.
		 */
		private int maxAckTimeout;
		/**
		 * Random factor for initial ACK retransmission timeout.
		 */
		private float ackRandomFactor;
		/**
		 * Scale for ACK retransmission timeout.
		 */
		private float ackTimeoutScale;
		/**
		 * Maximum number of retransmissions.
		 */
		private int maxRetransmit;
		/**
		 * NSTART, number of concurrent request/transmissions.
		 */
		private int nstart;

		private Builder() {
		}

		/**
		 * Apply value from {@link Configuration}.
		 * 
		 * Specific values may be adapted by further calls to other setter.
		 * 
		 * Provides a fluent API to chain setters.
		 * 
		 * @param config configuration
		 * @return this builder to chain setter.
		 * @throws IllegalStateException if {@link #maxAckTimeout} is less than
		 *             {@link #ackTimeout} or {@link #ackRandomFactor} or
		 *             {@link #ackTimeoutScale} is less than {@code 1.0}.
		 * @since 3.0 (changed parameter to Configuration, added IllegalStateException)
		 */
		public Builder applyConfig(Configuration config) {
			ackTimeout = config.getTimeAsInt(CoapConfig.ACK_TIMEOUT, TimeUnit.MILLISECONDS);
			maxAckTimeout = config.getTimeAsInt(CoapConfig.MAX_ACK_TIMEOUT, TimeUnit.MILLISECONDS);
			ackRandomFactor = config.get(CoapConfig.ACK_INIT_RANDOM);
			ackTimeoutScale = config.get(CoapConfig.ACK_TIMEOUT_SCALE);
			maxRetransmit = config.get(CoapConfig.MAX_RETRANSMIT);
			nstart = config.get(CoapConfig.NSTART);
			check();
			return this;
		}

		/**
		 * Set the initial ACK timeout.
		 * 
		 * Provides a fluent API to chain setters.
		 * 
		 * @param ackTimeout initial ACK timeout in milliseconds
		 * @return this builder to chain setter.
		 */
		public Builder ackTimeout(int ackTimeout) {
			this.ackTimeout = ackTimeout;
			return this;
		}

		/**
		 * Set the maximum ACK timeout.
		 * 
		 * Provides a fluent API to chain setters.
		 * 
		 * @param maxAckTimeout maximum ACK timeout in milliseconds
		 * @return this builder to chain setter.
		 * @since 3.0
		 */
		public Builder maxAckTimeout(int maxAckTimeout) {
			this.maxAckTimeout = maxAckTimeout;
			return this;
		}

		/**
		 * Set random factor for initial ACK retransmission timeout.
		 * 
		 * Provides a fluent API to chain setters.
		 * 
		 * @param ackRandomFactor random factor from 1.0f to upper limit
		 * @return this builder to chain setter.
		 */
		public Builder ackRandomFactor(float ackRandomFactor) {
			this.ackRandomFactor = ackRandomFactor;
			return this;
		}

		/**
		 * Set scale factor for ACK retransmission timeout.
		 * 
		 * Provides a fluent API to chain setters.
		 * 
		 * @param ackTimeoutScale scale factor
		 * @return this builder to chain setter.
		 */
		public Builder ackTimeoutScale(float ackTimeoutScale) {
			this.ackTimeoutScale = ackTimeoutScale;
			return this;
		}

		/**
		 * Set maximum number of retransmissions.
		 * 
		 * Provides a fluent API to chain setters.
		 * 
		 * @param maxRetransmit maximum number of retransmissions
		 * @return this builder to chain setter.
		 */
		public Builder maxRetransmit(int maxRetransmit) {
			this.maxRetransmit = maxRetransmit;
			return this;
		}

		/**
		 * Set NSTART, number of concurrent request/transmissions.
		 * 
		 * Note: Only experimentally supported by
		 * {@link CongestionControlLayer}, which has known threading issues.
		 * 
		 * @param nstart number of concurrent request/transmissions
		 * @return this builder to chain setter.
		 */
		public Builder nstart(int nstart) {
			this.nstart = nstart;
			return this;
		}

		/**
		 * Build ReliabilityLayerParameters.
		 * 
		 * @return initialized ReliabilityLayerParameters
		 * @throws IllegalStateException if {@link #maxAckTimeout} is less than
		 *             {@link #ackTimeout} or {@link #ackRandomFactor} or
		 *             {@link #ackTimeoutScale} is less than {@code 1.0}.
		 * @since 3.0 (added IllegalStateException)
		 */
		public ReliabilityLayerParameters build() {
			check();
			return new ReliabilityLayerParameters(ackTimeout, maxAckTimeout, ackRandomFactor, ackTimeoutScale,
					maxRetransmit, nstart);
		}

		/**
		 * Check values consistency.
		 * 
		 * @throws IllegalStateException if {@link #maxAckTimeout} is less than
		 *             {@link #ackTimeout} or {@link #ackRandomFactor} or
		 *             {@link #ackTimeoutScale} is less than {@code 1.0}.
		 * @since 3.0
		 */
		private void check() {
			if (maxAckTimeout < ackTimeout) {
				throw new IllegalStateException("Maximum ack timeout " + maxAckTimeout
						+ "ms must not be less than ack timeout " + ackTimeout + "ms!");
			}
			if (1 > maxRetransmit) {
				throw new IllegalStateException("Maxium retransmit " + maxRetransmit + " must not be less than 1!");
			}
			if (1 > nstart) {
				throw new IllegalStateException("Nstart " + nstart + " must not be less than 1!");
			}
			if (1.0 > ackRandomFactor) {
				throw new IllegalStateException("Ack random factor " + ackRandomFactor + " must not be less than 1.0!");
			}
			if (1.0 > ackTimeoutScale) {
				throw new IllegalStateException("Ack scale factor " + ackTimeoutScale + " must not be less than 1.0!");
			}
		}
	}
}
