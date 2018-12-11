/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.project;

import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.visibleWhen;
import static de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService.MAX_RECOMMENDATIONS_CAP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxFormComponentUpdatingBehavior;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxFormSubmittingBehavior;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaModelAdapter;
import de.tudarmstadt.ukp.clarin.webanno.support.spring.ApplicationEventPublisherHolder;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.ModelChangedVisitor;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommenderFactoryRegistry;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactory;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderDeletedEvent;

public class RecommenderEditorPanel
    extends Panel
{
    private static final long serialVersionUID = -5278078988218713188L;

    private static final String MID_CANCEL = "cancel";
    private static final String MID_DELETE = "delete";
    private static final String MID_SAVE = "save";
    private static final String MID_MAX_RECOMMENDATIONS = "maxRecommendations";
    private static final String MID_THRESHOLD = "threshold";
    private static final String MID_TRAITS_CONTAINER = "traitsContainer";
    private static final String MID_TRAITS = "traits";
    private static final String MID_FORM = "form";
    private static final String MID_NAME = "name";
    private static final String MID_FEATURE = "feature";
    private static final String MID_LAYER = "layer";
    private static final String MID_ENABLED = "enabled";
    private static final String MID_AUTO_GENERATED_NAME = "autoGenerateName";
    private static final String MID_ALWAYS_SELECTED = "alwaysSelected";
    private static final String MID_TOOL = "tool";
    private static final String MID_ACTIVATION_CONTAINER = "activationContainer";
    
    private @SpringBean RecommendationService recommendationService;
    private @SpringBean AnnotationSchemaService annotationSchemaService;
    private @SpringBean RecommenderFactoryRegistry recommenderRegistry;
    private @SpringBean ApplicationEventPublisherHolder appEventPublisherHolder;
    private @SpringBean UserDao userDao;

    private TextField<String> nameField;
    private WebMarkupContainer traitsContainer;
    private WebMarkupContainer activationContainer;
    private DropDownChoice<Pair<String, String>> toolChoice;
    private DropDownChoice<String> featureChoice;
    private DropDownChoice<AnnotationLayer> layerChoice;
    private CheckBox autoGenerateNameCheckBox;

    private IModel<Project> projectModel;
    private IModel<Recommender> recommenderModel;
    private boolean autoGenerateName;
    
    public RecommenderEditorPanel(String aId, IModel<Project> aProject,
            IModel<Recommender> aRecommender)
    {
        super(aId, aRecommender);
        
        setOutputMarkupId(true);
        setOutputMarkupPlaceholderTag(true);
        
        projectModel = aProject;
        recommenderModel = aRecommender;

        Form<Recommender> form = new Form<>(MID_FORM, CompoundPropertyModel.of(aRecommender));
        add(form);
        
        nameField = new TextField<>(MID_NAME, String.class);
        nameField.add(new RecommenderExistsValidator(projectModel, recommenderModel));
        nameField.setRequired(true);
        form.add(nameField);
        
        autoGenerateNameCheckBox = new CheckBox(MID_AUTO_GENERATED_NAME,
                PropertyModel.of(this, "autoGenerateName"));
        autoGenerateNameCheckBox.add(new LambdaAjaxFormComponentUpdatingBehavior("change", t -> {
            autoUpdateName(nameField, recommenderModel.getObject());
            t.add(nameField, autoGenerateNameCheckBox);
        }));
        form.add(autoGenerateNameCheckBox);
        
        form.add(new CheckBox(MID_ENABLED));
        
        layerChoice = new DropDownChoice<>(MID_LAYER,this::listLayers);
        layerChoice.setChoiceRenderer(new ChoiceRenderer<>("uiName"));
        layerChoice.setRequired(true);
        // The features and tools depend on the layer, so reload them when the layer is changed
        layerChoice.add(new LambdaAjaxFormComponentUpdatingBehavior("change", t -> { 
            toolChoice.setModelObject(null);
            featureChoice.setModelObject(null);
            autoUpdateName(nameField, recommenderModel.getObject());
            // Need to add the autoGenerateNameCheckBox here otherwise it looses its form-updating
            // behavior - no idea why
            t.add(nameField, autoGenerateNameCheckBox, form.get(MID_TOOL), form.get(MID_FEATURE),
                    form.get(MID_MAX_RECOMMENDATIONS), activationContainer, traitsContainer);
        }));
        form.add(layerChoice);
        
        featureChoice = new DropDownChoice<>(MID_FEATURE, this::listFeatures);
        featureChoice.setRequired(true);
        featureChoice.setOutputMarkupId(true);
        featureChoice.add(LambdaBehavior.onConfigure(_this -> {
            if (featureChoice.getChoicesModel().getObject().size() == 1) {
                featureChoice.setModelObject(featureChoice.getChoicesModel().getObject().get(0));
            }
        }));
        // The tools depend on the feature, so reload the tools when the feature is changed
        featureChoice.add(new LambdaAjaxFormComponentUpdatingBehavior("change", t -> {
            toolChoice.setModelObject(null);
            autoUpdateName(nameField, recommenderModel.getObject());
            // Need to add the autoGenerateNameCheckBox here otherwise it looses its form-updating
            // behavior - no idea why
            t.add(nameField, autoGenerateNameCheckBox, form.get(MID_TOOL),
                    form.get(MID_MAX_RECOMMENDATIONS), activationContainer, traitsContainer);
        }));
        form.add(featureChoice);
        
        IModel<Pair<String, String>> toolModel = LambdaModelAdapter.of(
            () -> {
                String name = recommenderModel.getObject().getTool();
                RecommendationEngineFactory factory = recommenderRegistry.getFactory(name);
                return factory != null ? Pair.of(factory.getId(), factory.getName()) : null;
            }, 
            (v) -> recommenderModel.getObject().setTool(v != null ? v.getKey() : null));
        
        toolChoice = new DropDownChoice<Pair<String, String>>(MID_TOOL, toolModel, this::listTools)
        {
            private static final long serialVersionUID = -1869081847783375166L;

            @Override
            protected void onModelChanged()
            {
                // If the feature type has changed, we need to set up a new traits editor
                Component newTraits;
                if (form.getModelObject() != null && getModelObject() != null) {
                    RecommendationEngineFactory factory = recommenderRegistry
                            .getFactory(getModelObject().getKey());
                    newTraits = factory.createTraitsEditor(MID_TRAITS, form.getModel());
                }
                else {
                    newTraits = new EmptyPanel(MID_TRAITS);
                }
                
                traitsContainer.addOrReplace(newTraits);
            }
        };
        // TODO: For a deprecated recommender, show itself in the tool dropdown but unselectable
        toolChoice.setChoiceRenderer(new ChoiceRenderer<Pair<String, String>>("value"));
        toolChoice.setRequired(true);
        toolChoice.setOutputMarkupId(true);
        toolChoice.add(new LambdaAjaxFormComponentUpdatingBehavior("change", t -> {
            autoUpdateName(nameField, recommenderModel.getObject());
            // Need to add the autoGenerateNameCheckBox here otherwise it looses its form-updating
            // behavior - no idea why
            t.add(nameField, autoGenerateNameCheckBox, form.get(MID_MAX_RECOMMENDATIONS),
                    activationContainer, traitsContainer);
        }));
        form.add(toolChoice);
        
        form.add(activationContainer = new WebMarkupContainer(MID_ACTIVATION_CONTAINER));
        activationContainer.setOutputMarkupPlaceholderTag(true);
        activationContainer.add(visibleWhen(() -> toolChoice.getModel().map(_tool -> 
                recommenderRegistry.getFactory(_tool.getKey()).isEvaluable())
                .orElse(false).getObject()));

        activationContainer.add(new CheckBox(MID_ALWAYS_SELECTED)
                .setOutputMarkupPlaceholderTag(true)
                .add(new LambdaAjaxFormSubmittingBehavior("change", t -> {
                    t.add(activationContainer.get(MID_THRESHOLD));
                })));

        activationContainer.add(new NumberTextField<>(MID_THRESHOLD, Float.class)
                .setMinimum(0.0f)
                .setMaximum(100.0f)
                .setStep(0.01f)
                .setOutputMarkupPlaceholderTag(true)
                .add(visibleWhen(() -> !recommenderModel.map(Recommender::isAlwaysSelected)
                        .orElse(false).getObject())));
        
        form.add(new NumberTextField<>(MID_MAX_RECOMMENDATIONS, Integer.class)
                .setMinimum(1)
                .setMaximum(MAX_RECOMMENDATIONS_CAP)
                .setStep(1)
                .setOutputMarkupPlaceholderTag(true)
                .add(visibleWhen(() -> toolChoice.getModel()
                                .map(_tool -> recommenderRegistry.getFactory(_tool.getKey())
                                        .isMultipleRecommendationProvider())
                                .orElse(false).getObject())));
        
        // Cannot use LambdaAjaxButton because it does not support onAfterSubmit.
        form.add(new AjaxButton(MID_SAVE)
        {
            private static final long serialVersionUID = -3902555252753037183L;

            @Override
            protected void onError(AjaxRequestTarget aTarget)
            {
                aTarget.addChildren(getPage(), IFeedback.class);
            };

            @Override
            protected void onAfterSubmit(AjaxRequestTarget target)
            {
                actionSave(target);
            };
        });
        
        form.add(new LambdaAjaxLink(MID_DELETE, this::actionDelete)
                .onConfigure(_this -> _this.setVisible(form.getModelObject().getId() != null)));
        form.add(new LambdaAjaxLink(MID_CANCEL, this::actionCancel)
                .onConfigure(_this -> _this.setVisible(form.getModelObject().getId() == null)));
        
        form.add(traitsContainer = new WebMarkupContainer(MID_TRAITS_CONTAINER));
        traitsContainer.setOutputMarkupPlaceholderTag(true);
        traitsContainer.add(new EmptyPanel(MID_TRAITS));
    }

    private void autoUpdateName(TextField<String> aField, Recommender aRecommender)
    {
        if (!autoGenerateName || aRecommender == null) {
            return;
        }
        
        aField.setModelObject(generateName(aRecommender));
    }
    
    private String generateName(Recommender aRecommender)
    {
        if (aRecommender.getFeature() == null || aRecommender.getLayer() == null
                || aRecommender.getTool() == null) {
            return null;
        }
        else {
            RecommendationEngineFactory factory = recommenderRegistry
                    .getFactory(aRecommender.getTool());
            return String.format(Locale.US, "[%s@%s] %s",
                    aRecommender.getLayer().getUiName(),
                    aRecommender.getFeature(), 
                    factory.getName());
        }
    }
    
    @Override
    protected void onConfigure()
    {
        super.onConfigure();
        
        setVisible(recommenderModel != null && recommenderModel.getObject() != null);
    }

    @Override
    protected void onModelChanged()
    {
        super.onModelChanged();

        // When field become invalid, Wicket stops re-rendering them. Thus we tell all of them that
        // their model has changes such that they clear their validation status.
        visitChildren(new ModelChangedVisitor(recommenderModel));

        // Since toolChoice uses a lambda model, it needs to be notified explicitly.
        toolChoice.modelChanged();
        
        // For new recommenders, default to auto-generation of name, for existing recommenders,
        // do not auto-generate name unless asked to
        Recommender recommender = recommenderModel.getObject();
        if (
                recommender.getId() == null || 
                Objects.equals(recommender.getName(), generateName(recommender))
        ) {
            autoGenerateNameCheckBox.setModelObject(true);
            autoUpdateName(nameField, recommenderModel.getObject());
        }
        else {
            autoGenerateNameCheckBox.setModelObject(false);
        }
    }

    private List<AnnotationLayer> listLayers()
    {
        List<AnnotationLayer> layers = new ArrayList<>();
        
        for (AnnotationLayer layer : annotationSchemaService
                .listAnnotationLayer(projectModel.getObject())) {
            if (WebAnnoConst.SPAN_TYPE.equals(layer.getType())
                    && !Token.class.getName().equals(layer.getName())) {
                layers.add(layer);
            }
        }
        
        return layers;
    }

    private List<String> listFeatures()
    {
        if (recommenderModel != null && recommenderModel.getObject().getLayer() != null) {
            List<String> features = new ArrayList<>();
            
            annotationSchemaService
                .listAnnotationFeature(recommenderModel.getObject().getLayer())
                .forEach(annotationFeature -> {
                    if (annotationFeature.getType() instanceof String) {
                        features.add(annotationFeature.getName());
                    }
                });   
            return features;
            
        } else {
            return Collections.emptyList();
        }
    }
    
    private List<Pair<String, String>> listTools()
    {
        if (recommenderModel != null && recommenderModel.getObject().getLayer() != null
                && recommenderModel.getObject().getFeature() != null) {
            AnnotationLayer layer = recommenderModel.getObject().getLayer();
            AnnotationFeature feature = annotationSchemaService
                    .getFeature(recommenderModel.getObject().getFeature(), layer);
            return recommenderRegistry.getFactories(layer, feature)
                .stream()
                .filter(f -> !f.isDeprecated())
                .map(f -> Pair.of(f.getId(), f.getName()))
                .collect(Collectors.toList());
        }
        else {
            return Collections.emptyList();
        }
    }

    private void actionSave(AjaxRequestTarget aTarget)
    {
        Recommender recommender = recommenderModel.getObject();
        recommender.setProject(recommender.getLayer().getProject());
        recommendationService.createOrUpdateRecommender(recommender);

        // causes deselection after saving
        recommenderModel.setObject(null);

        // Reload whole page because master panel also needs to be reloaded.
        aTarget.add(getPage());
    }
    
    private void actionDelete(AjaxRequestTarget aTarget) {
        recommendationService.deleteRecommender(recommenderModel.getObject());
        appEventPublisherHolder.get().publishEvent(
            new RecommenderDeletedEvent(this, recommenderModel.getObject(),
                userDao.getCurrentUser().getUsername(), projectModel.getObject()));
        actionCancel(aTarget);
    }
    
    private void actionCancel(AjaxRequestTarget aTarget) {
        recommenderModel.setObject(null);
        
        // Reload whole page because master panel also needs to be reloaded.
        aTarget.add(getPage());
    }
    
    private class RecommenderExistsValidator
        implements IValidator<String>
    {
        private static final long serialVersionUID = 8604561828541964271L;

        private IModel<Recommender> recommender;
        private IModel<Project> project;
        
        public RecommenderExistsValidator(IModel<Project> aProject,
                IModel<Recommender> aRecommender)
        {
            recommender = aRecommender;
            project = aProject;
        }
        
        @Override
        public void validate(IValidatable<String> aValidatable)
        {
            String newName = aValidatable.getValue();
            Recommender currentRecommender = recommender.getObject();
            Optional<Recommender> recommenderWithNewName = recommendationService
                    .getRecommender(project.getObject(), newName);
            // Either there should be no recommender with the new name already existing or it should
            // be the recommender we are currently editing (i.e. the name has not changed)
            if (
                    recommenderWithNewName.isPresent() &&
                    !Objects.equals(recommenderWithNewName.get().getId(),
                            currentRecommender.getId())
            ) {
                aValidatable.error(new ValidationError(
                        "Another recommender with the same name exists. Please try a different name"));
            }
        }
    }
}
