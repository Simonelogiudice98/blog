package com.simone.blog.validation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class SortValidator {

    private SortValidator() {

    }

    public static void checkValidity(Pageable pageable, Set<String> allowedFields){

        for(Sort.Order order : pageable.getSort()){
            if(!allowedFields.contains(order.getProperty())){
                throw new RuntimeException("Campo di sorting non valido: " + order.getProperty());
            }
        }

    }
}
