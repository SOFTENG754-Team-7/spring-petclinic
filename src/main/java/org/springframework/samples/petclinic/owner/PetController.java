/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manages pet creation and updates within the context of a specific owner.
 * Interacts with {@link OwnerRepository} to load owners and persist pet
 * changes, and with {@link PetTypeRepository} to provide reference data.
 * Sustainability: explicit validation and centralized loading reduce duplicate
 * logic, improve readability and maintainability, reduce onboarding effort,
 * and lower long-term development cost while protecting data integrity.
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Wick Dynex
 */
@Controller
@RequestMapping("/owners/{ownerId}")
class PetController {

	private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";

	private final OwnerRepository owners;

	private final PetTypeRepository types;

	/**
	 * Creates the controller with its required repositories.
	 * @param owners repository used for owner and pet persistence
	 * @param types repository used to load available pet types
	 */
	public PetController(OwnerRepository owners, PetTypeRepository types) {
		this.owners = owners;
		this.types = types;
	}

	/**
	 * Supplies pet type reference data for form rendering.
	 * @return collection of available pet types
	 */
	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		// Reference data caching is centralized to reduce duplication across views.
		return this.types.findPetTypes();
	}

	/**
	 * Resolves the owner for the current request scope.
	 * @param ownerId owner identifier from the route
	 * @return resolved owner
	 * @throws IllegalArgumentException if the owner does not exist
	 */
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		// Centralized loading improves reuse and reduces cognitive complexity.
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner;
	}

	/**
	 * Resolves the pet for edit flows or creates a new instance for create flows.
	 * @param ownerId owner identifier from the route
	 * @param petId optional pet identifier from the route
	 * @return resolved or new pet instance
	 * @throws IllegalArgumentException if the owner does not exist
	 */
	@ModelAttribute("pet")
	public Pet findPet(@PathVariable("ownerId") int ownerId,
			@PathVariable(name = "petId", required = false) Integer petId) {

		if (petId == null) {
			return new Pet();
		}

		// Owner lookup is shared to keep validation and loading consistent.
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner.getPet(petId);
	}

	/**
	 * Protects owner identifiers from being rebound by form submissions.
	 * @param dataBinder binder used to configure allowed and disallowed fields
	 */
	@InitBinder("owner")
	public void initOwnerBinder(WebDataBinder dataBinder) {
		// Prevent identifier tampering for data integrity.
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Registers validation for pet forms and protects identifier fields.
	 * @param dataBinder binder used to configure validation and allowed fields
	 */
	@InitBinder("pet")
	public void initPetBinder(WebDataBinder dataBinder) {
		// Validator enforces business rules consistently across create/edit flows.
		dataBinder.setValidator(new PetValidator());
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Initializes the pet creation form.
	 * @param owner resolved owner for the current request
	 * @param model model used to expose form state
	 * @return view name for the create/update pet form
	 */
	@GetMapping("/pets/new")
	public String initCreationForm(Owner owner, ModelMap model) {
		// Pre-associate a new pet with the owner for consistent binding.
		Pet pet = new Pet();
		owner.addPet(pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Validates and persists a new pet for an owner.
	 * @param owner resolved owner for the current request
	 * @param pet bound pet entity from the form
	 * @param result validation results for the submitted pet
	 * @param redirectAttributes attributes used to convey user feedback
	 * @return redirect to the owner details page or the form view on errors
	 */
	@PostMapping("/pets/new")
	public String processCreationForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		// Duplicate check prevents inconsistent state and reduces cleanup effort.
		if (StringUtils.hasText(pet.getName()) && pet.isNew() && owner.getPet(pet.getName(), true) != null) {
			result.rejectValue("name", "duplicate", "already exists");
		}

		// Date validation protects data quality and avoids future correction work.
		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		// Persist through the owner aggregate for consistent lifecycle handling.
		owner.addPet(pet);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Pet has been Added");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Displays the pet update form.
	 * @return view name for the create/update pet form
	 */
	@GetMapping("/pets/{petId}/edit")
	public String initUpdateForm() {
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Validates and persists edits to an existing pet.
	 * @param owner resolved owner for the current request
	 * @param pet bound pet entity from the form
	 * @param result validation results for the submitted pet
	 * @param redirectAttributes attributes used to convey user feedback
	 * @return redirect to the owner details page or the form view on errors
	 */
	@PostMapping("/pets/{petId}/edit")
	public String processUpdateForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		String petName = pet.getName();

		// checking if the pet name already exists for the owner
		// Duplicate check minimizes conflicting records and user confusion.
		if (StringUtils.hasText(petName)) {
			Pet existingPet = owner.getPet(petName, false);
			if (existingPet != null && !Objects.equals(existingPet.getId(), pet.getId())) {
				result.rejectValue("name", "duplicate", "already exists");
			}
		}

		// Date validation protects future reporting accuracy.
		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		// Consolidated update path reduces duplication and eases maintenance.
		updatePetDetails(owner, pet);
		redirectAttributes.addFlashAttribute("message", "Pet details has been edited");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Updates the pet details if it exists or adds a new pet to the owner.
	 * @param owner The owner of the pet
	 * @param pet The pet with updated details
	 */
	private void updatePetDetails(Owner owner, Pet pet) {
		Integer id = pet.getId();
		Assert.state(id != null, "'pet.getId()' must not be null");
		Pet existingPet = owner.getPet(id);
		if (existingPet != null) {
			// Update existing pet's properties
			existingPet.setName(pet.getName());
			existingPet.setBirthDate(pet.getBirthDate());
			existingPet.setType(pet.getType());
		}
		else {
			owner.addPet(pet);
		}
		this.owners.save(owner);
	}

}
