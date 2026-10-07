package com.team5.notifications.delivery;

import com.team5.preferences.AlertChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChannelSenderRegistry {

    private final Map<AlertChannel, ChannelSender> senders = new EnumMap<>(AlertChannel.class);

    public ChannelSenderRegistry(List<ChannelSender> channelSenders) {
        for (ChannelSender sender : channelSenders) {
            if (senders.putIfAbsent(sender.channel(), sender) != null) {
                throw new IllegalStateException("Two senders registered for channel " + sender.channel());
            }
        }
    }

    public Optional<ChannelSender> forChannel(AlertChannel channel) {
        return Optional.ofNullable(senders.get(channel));
    }
}
