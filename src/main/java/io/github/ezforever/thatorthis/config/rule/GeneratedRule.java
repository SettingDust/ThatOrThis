package io.github.ezforever.thatorthis.config.rule;

import io.github.ezforever.thatorthis.config.choice.*;
import io.github.ezforever.thatorthis.gui.SingleThreadFuture;
import io.github.ezforever.thatorthis.gui.Texts;
import io.github.ezforever.thatorthis.internal.DirectoryWalker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

// Rule with type = "GENERATED" - Leads to another screen filled with individual mods' options
public class GeneratedRule extends VisibleRule implements RuleHolder {
    private enum Options {
        ON("on", "@thatorthis.generated.on"),
        OFF("off", "@thatorthis.generated.off")
        ;

        // ---

        public final DefinedRule.Option option;
        public final DefinedRuleChoice choice;

        Options(String id, String caption) {
            option = new DefinedRule.Option(id, caption, Collections.emptySet(), false);
            choice = new DefinedRuleChoice(id);
        }
    }

    // ---

    // Directories to search mods from
    public final Set<String> directories;
    // Default blacklist
    public final Set<String> defaults;
    // Custom mod ID to name mapping
    public final Map<String, String> customNames;

    private transient List<Rule> fakeRules;

    public GeneratedRule(String id, String caption, String tooltip, Set<String> directories, Set<String> defaults, Map<String, String> customNames) {
        super(id, caption, tooltip);
        this.directories = Collections.unmodifiableSet(directories);
        this.defaults = Collections.unmodifiableSet(defaults);
        this.customNames = customNames == null ? null : Collections.unmodifiableMap(customNames);
    }

    // --- Extends VisibleRule

    @Override
    @Environment(EnvType.CLIENT)
    public SingleThreadFuture<Choice> updateChoice(Choice prevChoice) {
        if(fakeRules == null)
            getRules();

        ChoiceHolder translatedChoices = new ChoiceHolder(
                fakeRules.stream()
                    .collect(Collectors.toMap(
                            (Rule rule) -> rule.id,
                            (Rule rule) -> ((GeneratedRuleChoice)prevChoice).choices.contains(rule.id)
                                ? Options.OFF.choice : Options.ON.choice
                    ))
        );
        return showNestedScreen(new Choices(translatedChoices, ((GeneratedRuleChoice)prevChoice).disabled))
                .then((Choices newChoices)
                        -> new GeneratedRuleChoice(newChoices.choices.entrySet().stream()
                            .filter((Map.Entry<String, Choice> entry)
                                    -> ((DefinedRuleChoice)entry.getValue()).choice
                                        .equals(Options.OFF.choice.choice))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet()),
                            newChoices.disabled
                        )
                );
    }

    // --- Extends VisibleRule -> Rule

    @Override
    public Optional<Choice> getDefaultChoice() {
        return Optional.of(new GeneratedRuleChoice(defaults, false));
    }

    @Override
    public boolean resolve(Choice choice, Map<String, Set<String>> resultMap) {
        if(!(choice instanceof GeneratedRuleChoice realChoice))
            return false;

        if(canDisable() && realChoice.disabled != null && realChoice.disabled)
            LOGGER.debug("Mods under rule {} are skipped as per user request", id);
        else
            directories.forEach((String dir) -> resultMap.put(dir, realChoice.choices));
        return true;
    }

    // --- Implements RuleHolder

    @Override
    public boolean canDisable() {
        // Even if disabling is prohibited, one can just turn every mod off
        return true;
    }

    @Override
    public List<Rule> getRules() {
        if(fakeRules == null) {
            fakeRules = new ArrayList<>();
            directories.forEach((String modDir) -> DirectoryWalker.walk(modDir,
                    (ModMetadata info) -> {
                        // Build default order in so no need to override getDefaultChoices()
                        List<DefinedRule.Option> options = new ArrayList<>();
                        if(defaults.contains(info.getId())) {
                            options.add(Options.OFF.option);
                            options.add(Options.ON.option);
                        } else {
                            options.add(Options.ON.option);
                            options.add(Options.OFF.option);
                        }

                        String name;
                        if(customNames != null && customNames.containsKey(info.getId()))
                            name = Texts.getText(customNames.get(info.getId())).getString();
                        else
                            name = info.getName();
                        String caption = Texts.GENERATED_FORMAT.get(name).getString();
                        DefinedRule rule = new DefinedRule(info.getId(), caption, "", options);
                        fakeRules.add(rule);
                    }
            ));
            fakeRules = Collections.unmodifiableList(fakeRules);
        }
        return fakeRules;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public Text getScreenTitle() {
        return Texts.getText(caption);
    }

    // Resolving GeneratedRuleChoice does not involve nested rules
    @Override
    public Map<String, Set<String>> resolve(ChoiceHolder choices) {
        throw new UnsupportedOperationException();
    }
}
